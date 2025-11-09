package com.cloud_ml_app_thesis.intergration.isolated;

import com.cloud_ml_app_thesis.dto.request.model.ModelUpdateRequest;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.exception.UserNotFoundException;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.service.ModelService;
import jakarta.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import com.cloud_ml_app_thesis.entity.model.Model;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("docker")
public class OptimisticLockingIT {

    private static final Logger log = LoggerFactory.getLogger(OptimisticLockingIT.class);

    @Autowired
    private ModelRepository modelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ModelService modelService;

    private Model testModel;
    private User testOwner;

    @BeforeEach
    void setUp() {
        testModel = modelRepository.findAll().stream()
                .filter(model -> "test-model".equals(model.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Model 'Image Classifier CNN' not found"));

        testOwner = userRepository.findByUsername("bigspy")
                .orElseThrow(() -> new UserNotFoundException("User 'emma' not found"));

        log.info("🧪 Test setup: Model ID={}, Version={}", testModel.getId(), testModel.getVersion());
    }

    @Test
    void concurrentUpdateAndDelete() {
        Model target = testModel;
        User owner = testOwner;

        EntityManager updateEntityManager = entityManagerFactory.createEntityManager();
        EntityTransaction updateTx = updateEntityManager.getTransaction();
        updateTx.begin();

        Model modelForUpdate = updateEntityManager.find(Model.class, target.getId());
        ModelUpdateRequest updateRequest = new ModelUpdateRequest();
        updateRequest.setName("Test rename");
        updateRequest.setDescription("Test description");
        updateRequest.setPublic(true);
        updateRequest.setDataDescription(modelForUpdate.getDataDescription());

        modelForUpdate.setName(updateRequest.getName());

        TransactionTemplate deleteTemplate = new TransactionTemplate(transactionManager);
        deleteTemplate.executeWithoutResult(status -> modelService.deleteModel(target.getId(), owner));

        assertThatThrownBy(updateTx::commit)
                .isInstanceOf(RollbackException.class)
                .hasCauseInstanceOf(OptimisticLockException.class);

        if(updateTx.isActive()) {
            updateTx.rollback();
        }
        updateEntityManager.close();
    }

    @Test
    void testConcurrentUpdates_10Users() throws InterruptedException {
        int numberOfThreads = 10;
        concurrentUpdateTest(numberOfThreads);
    }

    @Test
    void testConcurrentUpdates_50Users() throws InterruptedException {
        int numberOfThreads = 50;
        concurrentUpdateTest(numberOfThreads);
    }

    @Test
    void testConcurrentUpdates_100Users() throws InterruptedException {
        int numberOfThreads = 100;
        concurrentUpdateTest(numberOfThreads);
    }

    /**
     * Core concurrent update test method.
     * Simulates multiple users trying to update the same model simultaneously.
     * Only ONE should succeed, all others should get OptimisticLockException.
     */
    private void concurrentUpdateTest(int numberOfThreads) throws InterruptedException {
        log.info("🚀 Starting concurrent update test with {} threads", numberOfThreads);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads)) {
            // Submit all tasks
            for (int i = 0; i < numberOfThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        // Wait for all threads to be ready
                        startLatch.await();

                        // Attempt to update the model
                        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                        txTemplate.execute(status -> {
                            Model model = modelRepository.findById(testModel.getId())
                                    .orElseThrow(() -> new IllegalStateException("Model not found"));

                            log.debug("Thread-{}: Read model version={}", threadId, model.getVersion());

                            // Simulate some processing time
                            try {
                                Thread.sleep(ThreadLocalRandom.current().nextInt(10, 50));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }

                            // Update the model
                            model.setDescription("Updated by thread-" + threadId + " at " + System.currentTimeMillis());
                            modelRepository.saveAndFlush(model);

                            log.info("✅ Thread-{}: Successfully updated model", threadId);
                            return null;
                        });

                        successCount.incrementAndGet();

                    } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                        log.debug("❌ Thread-{}: OptimisticLockException caught (expected)", threadId);
                        failureCount.incrementAndGet();
                        exceptions.add(e);
                    } catch (Exception e) {
                        log.error("Thread-{}: Unexpected exception", threadId, e);
                        exceptions.add(e);
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            // Start all threads at the same time
            log.info("🏁 Releasing all threads simultaneously...");
            startLatch.countDown();

            // Wait for all threads to complete
            boolean completed = completionLatch.await(30, TimeUnit.SECONDS);

            // Assertions
            assertThat(completed).isTrue();
            log.info("📊 Results: Success={}, Failures={}", successCount.get(), failureCount.get());

            // At least one thread should succeed
            assertThat(successCount.get())
                    .as("At least one update should succeed")
                    .isGreaterThanOrEqualTo(1);

            // Most threads should fail due to optimistic locking
            assertThat(failureCount.get())
                    .as("Multiple threads should fail with OptimisticLockException")
                    .isGreaterThan(0);

            // Total should equal number of threads
            assertThat(successCount.get() + failureCount.get())
                    .as("All threads should complete")
                    .isEqualTo(numberOfThreads);

            // All failures should be optimistic locking related
            for (Exception e : exceptions) {
                assertThat(e)
                        .isInstanceOfAny(
                                ObjectOptimisticLockingFailureException.class,
                                OptimisticLockException.class,
                                RollbackException.class
                        );
            }
        } // ExecutorService will be automatically closed here
    }

    @Test
    void testConcurrentUpdatesWithRetry() throws InterruptedException {
        log.info("🚀 Starting concurrent update test with retry logic");

        int numberOfThreads = 20;
        int maxRetries = 3;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger totalSuccessCount = new AtomicInteger(0);
        AtomicInteger totalAttempts = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads)) {
            // Submit all tasks
            for (int i = 0; i < numberOfThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        boolean success = false;
                        int retryCount = 0;

                        while (!success && retryCount <= maxRetries) {
                            final int currentAttempt = retryCount;
                            try {
                                totalAttempts.incrementAndGet();

                                TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                                txTemplate.execute(status -> {
                                    Model model = modelRepository.findById(testModel.getId())
                                            .orElseThrow(() -> new IllegalStateException("Model not found"));

                                    model.setDescription("Updated by thread-" + threadId + " (attempt " + currentAttempt + ")");
                                    modelRepository.saveAndFlush(model);
                                    return null;
                                });

                                success = true;
                                totalSuccessCount.incrementAndGet();
                                log.info("✅ Thread-{}: Success on attempt {}", threadId, currentAttempt);

                            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                                retryCount++;
                                if (retryCount <= maxRetries) {
                                    log.debug("🔄 Thread-{}: Retry {} after OptimisticLockException", threadId, retryCount);
                                    Thread.sleep(ThreadLocalRandom.current().nextInt(10, 100));
                                } else {
                                    log.warn("❌ Thread-{}: Failed after {} retries", threadId, maxRetries);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Thread-{}: Error", threadId, e);
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(60, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            log.info("📊 Results: Total Success={}, Total Attempts={}", totalSuccessCount.get(), totalAttempts.get());

            // With retries, all threads should eventually succeed
            assertThat(totalSuccessCount.get())
                    .as("All threads should eventually succeed with retries")
                    .isEqualTo(numberOfThreads);

            // Total attempts should be greater than number of threads (due to retries)
            assertThat(totalAttempts.get())
                    .as("Total attempts should exceed thread count due to retries")
                    .isGreaterThan(numberOfThreads);
        } // ExecutorService will be automatically closed here
    }

    @Test
    void testConcurrentMixedOperations() throws InterruptedException {
        log.info("🚀 Starting mixed concurrent operations test");

        int numberOfUpdates = 15;
        int numberOfReads = 10;
        int totalThreads = numberOfUpdates + numberOfReads;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(totalThreads);

        AtomicInteger updateSuccessCount = new AtomicInteger(0);
        AtomicInteger updateFailureCount = new AtomicInteger(0);
        AtomicInteger readCount = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newFixedThreadPool(totalThreads)) {
            // Submit update tasks
            for (int i = 0; i < numberOfUpdates; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                        txTemplate.execute(status -> {
                            Model model = modelRepository.findById(testModel.getId())
                                    .orElseThrow(() -> new IllegalStateException("Model not found"));

                            try {
                                Thread.sleep(ThreadLocalRandom.current().nextInt(10, 50));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                            model.setName("Updated-" + threadId);
                            modelRepository.saveAndFlush(model);
                            return null;
                        });

                        updateSuccessCount.incrementAndGet();
                        log.debug("✅ Update thread-{}: Success", threadId);

                    } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                        updateFailureCount.incrementAndGet();
                        log.debug("❌ Update thread-{}: OptimisticLockException", threadId);
                    } catch (Exception e) {
                        log.error("Update thread-{}: Error", threadId, e);
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            // Submit read tasks (should always succeed)
            for (int i = 0; i < numberOfReads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                        txTemplate.setReadOnly(true);
                        txTemplate.execute(status -> {
                            Model model = modelRepository.findById(testModel.getId())
                                    .orElseThrow(() -> new IllegalStateException("Model not found"));

                            log.debug("📖 Read thread-{}: Model name={}, version={}", threadId, model.getName(), model.getVersion());
                            return null;
                        });

                        readCount.incrementAndGet();

                    } catch (Exception e) {
                        log.error("Read thread-{}: Error", threadId, e);
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(30, TimeUnit.SECONDS);

            assertThat(completed).isTrue();
            log.info("📊 Results: Update Success={}, Update Failures={}, Reads={}",
                    updateSuccessCount.get(), updateFailureCount.get(), readCount.get());

            // All reads should succeed
            assertThat(readCount.get())
                    .as("All read operations should succeed")
                    .isEqualTo(numberOfReads);

            // At least one update should succeed
            assertThat(updateSuccessCount.get())
                    .as("At least one update should succeed")
                    .isGreaterThanOrEqualTo(1);

            // Total updates should match
            assertThat(updateSuccessCount.get() + updateFailureCount.get())
                    .as("All update operations should complete")
                    .isEqualTo(numberOfUpdates);
        } // ExecutorService will be automatically closed here
    }
}
