package com.cloud_ml_app_thesis.intergration.extra;

import com.cloud_ml_app_thesis.dto.request.model.ModelUpdateRequest;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.exception.UserNotFoundException;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.service.ModelService;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.cloud_ml_app_thesis.entity.model.Model;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("docker")
public class OptimisticLockingIT {

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

    @Test
    void concurrentUpdateAndDelete() {
        Model target = modelRepository.findAll().stream()
                        .filter(model -> "Image Classifier CNN".equals(model.getName()))
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException("Model not found"));

        User owner = userRepository.findByUsername("emma").orElseThrow(() -> new UserNotFoundException("User not found"));

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
}
