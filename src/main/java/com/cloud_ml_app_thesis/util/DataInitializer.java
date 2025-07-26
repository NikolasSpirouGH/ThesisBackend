
package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.accessibility.CustomAlgorithmAccessibility;
import com.cloud_ml_app_thesis.entity.accessibility.DatasetAccessibility;
import com.cloud_ml_app_thesis.entity.accessibility.ModelAccessibility;
import com.cloud_ml_app_thesis.entity.status.*;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.DatasetAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.status.*;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.accessibility.AlgorithmAccessibilityRepository;
import com.cloud_ml_app_thesis.repository.accessibility.DatasetAccessibilityRepository;
import com.cloud_ml_app_thesis.repository.accessibility.ModelAccessibilityRepository;
import com.cloud_ml_app_thesis.repository.status.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
import weka.classifiers.Classifier;
import weka.clusterers.Clusterer;
import weka.core.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserStatusRepository userStatusRepository;
    private final AlgorithmRepository algorithmRepository;
    private final CategoryRepository categoryRepository;
    private final TrainingStatusRepository trainingStatusRepository;
    private final ModelStatusRepository modelStatusRepository;
    private final DatasetAccessibilityRepository datasetAccessibilityRepository;
    private final ModelAccessibilityRepository modelAccessibilityRepository;
    private final AlgorithmTypeRepository algorithmTypeRepository;

    private final String adminPassword = "adminPassword"; // Replace with actual password retrieval
    private final String userPassword = "userPassword"; // Replace with actual password retrieval
    private final Argon2PasswordEncoder passwordEncoder;
    private final ModelTypeRepository modelTypeRepository;
    private final AlgorithmAccessibilityRepository algorithmAccessibilityRepository;
    private final ModelExecutionStatusRepository modelExecutionStatusRepository;
    private final CategoryRequestStatusRepository categoryRequestStatusRepository;

    @Override
    public void run(String... args) {
        initializeUserStatuses();
        initializeTrainingStatuses();
        initializeModelStatuses();
        initializeDatasetAccessibility();
        initializeUserRoles();
        initializeModelAccessibilities();
        initializeModelTypes();
        initializeAlgorithmTypes();
        initializeAlgorithmAccessibilities();
        initializeAlgorithms();
        initializeModelExecutionStatuses();
        recreateAdmins();
        initializeCategoriesStatuses();
        initializeCategories();

    }

    private void initializeAlgorithmTypes() {
        Arrays.stream(AlgorithmTypeEnum.values()).forEach(type -> {
            algorithmTypeRepository.findByName(type).orElseGet(() -> {
                System.out.println("Initializing AlgorithmType " + type);
                return algorithmTypeRepository.save(new AlgorithmType(type));
            });
        });
    }

    private void initializeCategoriesStatuses() {
        Arrays.stream(CategoryRequestStatusEnum.values()).forEach(statusEnum -> {
            CategoryRequestStatus status = categoryRequestStatusRepository.findByName(statusEnum)
                    .orElseGet(() -> {
                        CategoryRequestStatus newStatus = new CategoryRequestStatus();
                        newStatus.setName(statusEnum);
                        newStatus.setDescription(getDescriptionForCategoryStatus(statusEnum));
                        return newStatus;
                    });

            if(status.getDescription() == null || status.getDescription().isBlank()) {
                status.setDescription(getDescriptionForCategoryStatus(statusEnum));
                categoryRequestStatusRepository.save(status);
            }
        });
    }

    private String getDescriptionForCategoryStatus(CategoryRequestStatusEnum statusEnum) {
        return switch (statusEnum) {
            case PENDING -> "The request is Pending";
            case APPROVED -> "The request is Approved";
            case REJECTED -> "The request is Rejected";
        };
    }

    private void initializeModelExecutionStatuses(){
        if (modelExecutionStatusRepository.count() == 0) {
            List<ModelExecutionStatus> modelStatusesList = new ArrayList<>();
            for(int i = 0; i< ModelExecutionStatusEnum.values().length; i++){
                modelStatusesList.add(new ModelExecutionStatus(null, ModelExecutionStatusEnum.values()[i], "Some description"));
            }
            modelExecutionStatusRepository.saveAll(modelStatusesList);
        }
    }

    private void initializeAlgorithmAccessibilities() {
        if (algorithmAccessibilityRepository.count() == 0) {
            List<CustomAlgorithmAccessibility> accessibilities = Arrays.stream(AlgorithmAccessibiltyEnum.values())
                    .map(value -> new CustomAlgorithmAccessibility(null, value, "Default description"))
                    .toList();
            algorithmAccessibilityRepository.saveAll(accessibilities);
            System.out.println("✅ Initialized model accessibilities.");
        }
    }

    private void initializeModelTypes() {
        Arrays.stream(ModelTypeEnum.values()).forEach(type -> {
            modelTypeRepository.findByName(type).orElseGet(() -> {
                System.out.println("Initializing Model type " + type);
                return modelTypeRepository.save(new ModelType(type));
            });
        });
    }

    private void initializeModelAccessibilities() {
        if (modelAccessibilityRepository.count() == 0) {
            List<ModelAccessibility> accessibilities = Arrays.stream(ModelAccessibilityEnum.values())
                    .map(value -> new ModelAccessibility(null, value, "Default description"))
                    .toList();
            modelAccessibilityRepository.saveAll(accessibilities);
            System.out.println("✅ Initialized model accessibilities.");
        }
    }

    private void initializeCategories() {
        boolean alreadyExists = categoryRepository.existsById(1);
        if (alreadyExists) {
            System.out.println("✅ Default category with ID 1 already exists.");
            return;
        }

        User createdBy = userRepository.findByUsername("bigspy")
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        Category defaultCategory = new Category();
        defaultCategory.setName("Default");
        defaultCategory.setDescription("Fallback category for datasets.");
        defaultCategory.setCreatedBy(createdBy);

        try {
            categoryRepository.save(defaultCategory);
            System.out.println("✅ Default category created successfully.");
        } catch (Exception e) {
            System.err.println("❌ Failed to create default category: " + e.getMessage());
        }
    }


    private void initializeUserRoles(){
        if (roleRepository.count() == 0) {
            List<Role> userRolesList = new ArrayList<>();
            for(int i=0; i< UserRoleEnum.values().length; i++){
                userRolesList.add(new Role(null, UserRoleEnum.values()[i], "Some description", null));
            }
            roleRepository.saveAll(userRolesList);
        }
    }
    private void initializeUserStatuses(){
        if (userStatusRepository.count() == 0) {
            List<UserStatus> userStatusesList = new ArrayList<>();
            for(int i=0; i< UserStatusEnum.values().length; i++){
                userStatusesList.add(new UserStatus(null, UserStatusEnum.values()[i], "Some description"));
            }
            userStatusRepository.saveAll(userStatusesList);
        }
    }
    private void initializeTrainingStatuses(){
        if (trainingStatusRepository.count() == 0) {
            List<TrainingStatus> trainingStatusesList = new ArrayList<>();
            for(int i=0; i< TrainingStatusEnum.values().length; i++){
                trainingStatusesList.add(new TrainingStatus(null, TrainingStatusEnum.values()[i], "Some description"));
            }
            trainingStatusRepository.saveAll(trainingStatusesList);
        }
    }
    private void initializeModelStatuses(){
        if (modelStatusRepository.count() == 0) {
            List<ModelStatus> modelStatusesList = new ArrayList<>();
            for(int i=0; i< ModelStatusEnum.values().length; i++){
                modelStatusesList.add(new ModelStatus(null, ModelStatusEnum.values()[i], "Some description"));
            }
            modelStatusRepository.saveAll(modelStatusesList);
        }
    }
    private void initializeDatasetAccessibility(){
        if (datasetAccessibilityRepository.count() == 0) {
            List<DatasetAccessibility> datasetAccessibilityList = new ArrayList<>();
            for(int i = 0; i< DatasetAccessibilityEnum.values().length; i++){
                datasetAccessibilityList.add(new DatasetAccessibility(null, DatasetAccessibilityEnum.values()[i], "Some description"));
            }
            datasetAccessibilityRepository.saveAll(datasetAccessibilityList);
        }
    }
    @Transactional
    public void recreateAdmins() {
        UserStatus defaultStatus = userStatusRepository.findByName(UserStatusEnum.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Default status not found"));
        Role userRole = roleRepository.findByName(UserRoleEnum.USER)
                .orElseThrow(() -> new RuntimeException("Role USER not found"));
        Role adminRole = roleRepository.findByName(UserRoleEnum.ADMIN)
                .orElseThrow(() -> new RuntimeException("Role ADMIN not found"));

        List<User> admins = List.of(
                new User(null, "bigspy", "Nikolas", "Spirou", "nikolas@gmail.com", passwordEncoder.encode(adminPassword), 27, "Senior SWE", "Greece", Set.of(adminRole), defaultStatus, null, null, null, null),
                new User(null, "nickriz", "Nikos", "Rizogiannis", "rizo@gmail.com", passwordEncoder.encode(adminPassword), 27, "Senior SWE", "Greece", Set.of(userRole), defaultStatus, null, null, null, null),
                new User(null, "johnken", "John", "Kennedy", "john@gmail.com", passwordEncoder.encode(userPassword), 27, "Senior SWE", "Greece", Set.of(adminRole), defaultStatus, null, null, null, null)
        );

        for (User admin : admins) {
            Optional<User> existingByEmail = userRepository.findByEmail(admin.getEmail());
            Optional<User> existingByUsername = userRepository.findByUsername(admin.getUsername());

            if (existingByEmail.isPresent()) {
                User existing = existingByEmail.get();
                existing.setUsername(admin.getUsername());
                existing.setFirstName(admin.getFirstName());
                existing.setLastName(admin.getLastName());
                existing.setPassword(admin.getPassword());
                existing.setAge(admin.getAge());
                existing.setProfession(admin.getProfession());
                existing.setCountry(admin.getCountry());
                existing.setRoles(admin.getRoles());
                existing.setStatus(admin.getStatus());
                userRepository.save(existing); // update
            } else if (existingByUsername.isPresent()) {
                System.out.printf("Skipping user '%s' – username already exists with different email%n", admin.getUsername());
            } else {
                userRepository.save(admin); // insert safely
            }
        }

        System.out.println("✅ Admins updated/inserted safely.");
    }


    private void initializeAlgorithms() {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage("weka.classifiers"))
                .addUrls(ClasspathHelper.forPackage("weka.clusterers"))
                .setScanners(new SubTypesScanner()));

        Set<Class<? extends Classifier>> classifierClasses = reflections.getSubTypesOf(Classifier.class);
        Set<Class<? extends Clusterer>> clustererClasses = reflections.getSubTypesOf(Clusterer.class);

        List<Algorithm> algorithmInfos = new ArrayList<>();

        processAlgorithmClasses(classifierClasses, algorithmInfos, Classifier.class);
        processAlgorithmClasses(clustererClasses, algorithmInfos, Clusterer.class);

        saveAlgorithms(algorithmInfos);
    }

    private <T> void processAlgorithmClasses(Set<Class<? extends T>> classes, List<Algorithm> algorithmInfos, Class<T> type) {
        for (Class<? extends T> cls : classes) {
            try {
                // Skip abstract classes, interfaces, or inner classes
                if (java.lang.reflect.Modifier.isAbstract(cls.getModifiers()) || cls.isInterface() || cls.isMemberClass()) {
                    continue;
                }

                T instance = cls.getDeclaredConstructor().newInstance();
                String className = cls.getSimpleName();
                // Retrieve the package path relative to "weka.classifiers" or other packages
                Package pkg = cls.getPackage();
                String packageName = pkg.getName();
                String relativePath = packageName.substring(packageName.indexOf("weka.") + "weka.".length());

                // Check if globalInfo method is available
                Method globalInfoMethod = null;
                try {
                    globalInfoMethod = cls.getMethod("globalInfo");
                } catch (NoSuchMethodException e) {
                    // globalInfo method is not available
                }

                String classInfo = "(no description available)";
                if (globalInfoMethod != null) {
                    classInfo = (String) globalInfoMethod.invoke(instance);
                }

                String[] optionsDefaultArr = new String[]{};

                // Listing options if the algorithm implements OptionHandler
                if (instance instanceof OptionHandler) {
                    OptionHandler optionHandler = (OptionHandler) instance;
                    Enumeration<Option> options = optionHandler.listOptions();
                    StringBuilder optionsStr = new StringBuilder();
                    StringBuilder optionsDescrStr = new StringBuilder();
                    while (options.hasMoreElements()) {
                        Option option = options.nextElement();
                        if (option.name() != null && !option.name().isBlank()) {
                            optionsStr.append(option.name()).append(",");
                            optionsDescrStr.append(option.description()).append("->");
                        }
                        optionsDefaultArr = optionHandler.getOptions();
                    }
                    if (!optionsStr.isEmpty()) {
                        optionsStr.deleteCharAt(optionsStr.length() - 1); // Remove trailing comma
                    }
                    if (optionsDescrStr.length() > 1) {
                        optionsDescrStr.delete(optionsDescrStr.length() - 2, optionsDescrStr.length()); // Remove trailing "->"
                    }

                    String defaultOptionsFinal = String.join(" ", optionsDefaultArr);

                    AlgorithmTypeEnum typeEnum;
                    if (Clusterer.class.isAssignableFrom(cls)) {
                        typeEnum = AlgorithmTypeEnum.CLUSTERING;
                    } else if (Classifier.class.isAssignableFrom(cls)) {
                        typeEnum = AlgorithmTypeEnum.CLASSIFICATION;
                    } else {
                        throw new IllegalArgumentException("❌ Unknown algorithm type: " + cls.getName());
                    }

                    AlgorithmType algorithmType = algorithmTypeRepository.findByName(typeEnum)
                            .orElseThrow(() -> new EntityNotFoundException("Algorithm Type not found: " + typeEnum));

                    Algorithm algorithm = new Algorithm(
                            null,
                            className,
                            classInfo.replaceAll("[\\t\\n]", ""),
                            algorithmType,
                            optionsStr.toString().replaceAll("[\\t\\n]", "").replace(",,", ","),
                            optionsDescrStr.toString().replaceAll("[\\t\\n]", "").replace("->->", "->"),
                            defaultOptionsFinal,
                            cls.getName());
                    algorithmInfos.add(algorithm);
                }
            } catch (Exception e) {
                System.err.println("Error processing class " + cls.getName() + ": " + e.getMessage());
            }
        }
    }

    private void saveAlgorithms(List<Algorithm> algorithmInfos) {
        Algorithm al = null;
        try {
            for (Algorithm a : algorithmInfos) {
                al = a;
                algorithmRepository.save(a);
            }
        } catch (DataIntegrityViolationException e) {
            System.out.println("Failed to save algorithm with name: " + (al != null ? al.getName() : "unknown") +
                    ", Description length: " + (al != null ? al.getDescription().length() : "unknown"));
            System.out.println("Options Description length: " + (al != null ? al.getOptionsDescription().length() : "unknown"));
        }
    }

    public static boolean isRegressor(Classifier classifier) {
        try {
            // 1. Create dummy dataset
            ArrayList<Attribute> attributes = new ArrayList<>();
            attributes.add(new Attribute("feature1"));
            attributes.add(new Attribute("feature2"));
            attributes.add(new Attribute("target")); // numeric class

            Instances data = new Instances("dummy", attributes, 0);
            data.setClassIndex(data.numAttributes() - 1);

            // Add dummy data
            double[] vals1 = {1.0, 2.0, 3.0};
            double[] vals2 = {2.0, 3.0, 4.0};
            double[] target = {10.0, 15.0, 20.0};

            for (int i = 0; i < vals1.length; i++) {
                double[] instance = {vals1[i], vals2[i], target[i]};
                data.add(new DenseInstance(1.0, instance));
            }

            // 2. Try to build model
            classifier.buildClassifier(data);

            // 3. Check if classAttribute is numeric
            return data.classAttribute().isNumeric();
        } catch (Exception e) {
            return false;
        }
    }
}