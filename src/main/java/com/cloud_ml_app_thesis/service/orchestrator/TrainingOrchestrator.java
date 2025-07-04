package com.cloud_ml_app_thesis.service.orchestrator;

import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.dto.request.training.TrainingStartRequest;
import com.cloud_ml_app_thesis.dto.response.*;
import com.cloud_ml_app_thesis.enumeration.DatasetFunctionalTypeEnum;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.dataset.DatasetRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
import com.cloud_ml_app_thesis.service.AlgorithmService;
import com.cloud_ml_app_thesis.service.DatasetService;
import com.cloud_ml_app_thesis.service.ModelService;
import com.cloud_ml_app_thesis.util.DatasetUtil;
import com.cloud_ml_app_thesis.util.ValidationUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import weka.core.Instances;

@Component
@RequiredArgsConstructor
public class TrainingOrchestrator {

    private final TrainingRepository trainingRepository;
    private final ModelRepository modelRepository;

    private final UserRepository userRepository;

    private final AlgorithmConfigurationRepository algorithmConfigurationRepository;

    private final AlgorithmRepository algorithmRepository;

    private final TrainingStatusRepository trainingStatusRepository;

    private final DatasetRepository datasetRepository;

    private final DatasetConfigurationRepository datasetConfigurationRepository;

    private final ModelService modelService;

    private final DatasetService datasetService;

    private final AlgorithmService algorithmService;



    public TrainingDataInput configureTrainingDataInputByTrainCase(TrainingStartRequest trainingRequest, User user) throws Exception {

        MultipartFile file = trainingRequest.getFile();
        boolean multipartFileExist = ValidationUtil.multipartFileExist(trainingRequest.getFile());

        String datasetId = trainingRequest.getDatasetId();
        boolean datasetIdExist = ValidationUtil.stringExists(datasetId);

        String datasetConfigurationId = trainingRequest.getDatasetConfigurationId();
        boolean datasetConfigurationIdExist = ValidationUtil.stringExists(datasetConfigurationId);

        String basicCharacteristicsColumns = trainingRequest.getBasicCharacteristicsColumns();
        boolean basicCharacteristicsColumnsExist = ValidationUtil.stringExists(basicCharacteristicsColumns);

        String targetClassColumn = trainingRequest.getTargetClassColumn();
        boolean targetClassColumnExist = ValidationUtil.stringExists(targetClassColumn);

        String algorithmId = trainingRequest.getAlgorithmId();
        boolean algorithmIdExist = ValidationUtil.stringExists(algorithmId);

        String algorithmOptions = trainingRequest.getAlgorithmOptions();
        boolean algorithmOptionsExist = ValidationUtil.stringExists(algorithmOptions);

        String algorithmConfigurationId = trainingRequest.getAlgorithmConfigurationId();
        boolean algorithmConfigurationIdExist = ValidationUtil.stringExists(algorithmConfigurationId);

        String trainingId = trainingRequest.getTrainingId();
        boolean trainingIdExist = ValidationUtil.stringExists(trainingId);

        String modelId = trainingRequest.getModelId();
        boolean modelIdExist = ValidationUtil.stringExists(modelId);

        TrainingDataInput trainingDataInput = new TrainingDataInput();
        DatasetConfiguration datasetConfiguration = null;
        // 1st check - Can't provide trainingId and modelId at the same time
        if(trainingIdExist && modelIdExist){
            trainingDataInput.setErrorResponse(new GenericResponse(null, "", "You can't train a model based on a Training and a Model at the same time.", new Metadata()));
            return trainingDataInput;
        }

        // 2nd check - Can't provide datasetId and a new File
        if(datasetIdExist && multipartFileExist){
            trainingDataInput.setErrorResponse(new GenericResponse(null, "", "You can't train a model with an already uploaded Dataset and a new Dataset File at the same time.", new Metadata()));
            return trainingDataInput;
        }

        // 3rd check - Can't provide datasetId and datasetConfigurationId at the same time
        if(datasetIdExist && datasetConfigurationIdExist){
            trainingDataInput.setErrorResponse(new GenericResponse(null, "", "You can't train a model providing a datasetId and a datasetConfigurationId at the same time.", new Metadata()));
            return trainingDataInput;
        }

        // 4th check - Can't provide datasetId and datasetConfigurationId at the same time
        if(datasetConfigurationIdExist && basicCharacteristicsColumnsExist && targetClassColumnExist){
            trainingDataInput.setErrorResponse(new GenericResponse(null, "", "You can't train a model providing a datasetConfigurationId and booth basic characteristics columns and target class column at the same time.", new Metadata()));
            return trainingDataInput;
        }

        // 5th check - Can't provide algorithmId and algorithmConfigurationId at the same time
        if(algorithmIdExist && algorithmConfigurationIdExist){
            trainingDataInput.setErrorResponse(new GenericResponse(null, "", "You can't train a model providing a algorithmId and a algorithmConfigurationId at the same time.", new Metadata()));
            return trainingDataInput;
        }
        // 6th check - Can't provide algorithmId and algorithmConfigurationId at the same time
        if(algorithmConfigurationIdExist && algorithmOptionsExist){
            trainingDataInput.setErrorResponse(new GenericResponse(null, "", "You can't train a model providing algorithm options and a algorithmConfigurationId at the same time.", new Metadata()));
            return trainingDataInput;
        }

        if(algorithmConfigurationIdExist && datasetConfigurationIdExist && targetClassColumnExist && basicCharacteristicsColumnsExist && algorithmOptionsExist){
            trainingDataInput.setErrorResponse(new GenericResponse(null, "", "You can't retrain a model providing all the configuration again. Please start a new train." , new Metadata()));
            return trainingDataInput;
        }

        // 7th check - If MultiPartFile is provided then upload the dataset and get the datasetId to continue
        if(multipartFileExist){
            GenericResponse<Dataset> uploadFileResponse = datasetService.uploadDataset(file, user, DatasetFunctionalTypeEnum.TRAIN);
             datasetId = ((Dataset)uploadFileResponse.getDataHeader()).getId().toString();
                datasetIdExist = true;
                trainingDataInput.setErrorResponse((GenericResponse) uploadFileResponse);
                trainingDataInput.setErrorResponse(new GenericResponse(null, "", "You can't retrain a model providing all the configuration again. Please start a new train." , new Metadata()));
        }

        //*********** CONFIGURING TRAINING DATASET**************
        //A) DATASET config
        /* 1st Dataset CASE - datasetId-Or-MultipartFile AND OPTIONALLY at least one of the [basicCharacteristicsColumns, targetClassColumn]
            - if not provided, then last column is the target class and all the previous columns are basic characteristics
        */
        
        if(datasetIdExist){
            //first set the DatasetConfiguration
            //TODO change the Exception message
            datasetConfiguration = new DatasetConfiguration();
            Dataset dataset = datasetRepository.findById(Integer.parseInt(datasetId)).orElseThrow(() -> new EntityNotFoundException("The dataset for your training could not be found!"));
            datasetConfiguration.setDataset(dataset);
            if(basicCharacteristicsColumnsExist){
                datasetConfiguration.setBasicAttributesColumns(basicCharacteristicsColumns);
            }
            if(targetClassColumnExist){
                datasetConfiguration.setTargetColumn(targetClassColumn);
            }
            datasetConfiguration = datasetConfigurationRepository.save(datasetConfiguration);

            Instances finalDataset = null;
            if(!multipartFileExist){
                finalDataset = datasetService.loadDatasetInstancesByDatasetConfigurationFromMinio(datasetConfiguration);
            } else{
                finalDataset = DatasetUtil.prepareDataset(file, dataset.getFileName(), datasetConfiguration);
            }
            trainingDataInput.setDataset(finalDataset);
        } /* 2nd Dataset CASE - datasetConfigurationID AND OPTIONALLY ONLY one of the [basicCharacteristicsColumns, targetClassColumn]
            - if something not provided, then the already defined dataset-characteristics of DatasetConfiguration will be set
        */
        else if (datasetConfigurationIdExist) {
            datasetConfiguration = datasetConfigurationRepository.findById(Integer.parseInt(datasetConfigurationId)).orElseThrow(()-> new EntityNotFoundException("The Dataset Configuration you provided could not be found."));
            if(basicCharacteristicsColumnsExist){
                datasetConfiguration.setBasicAttributesColumns(basicCharacteristicsColumns);
            }else if(targetClassColumnExist){
                datasetConfiguration.setTargetColumn(targetClassColumn);
            }
            Instances finalDataset = datasetService.loadDatasetInstancesByDatasetConfigurationFromMinio(datasetConfiguration);
            trainingDataInput.setDataset(finalDataset);

        }

        //*********** END OF CONFIGURING TRAINING DATASET**************

        //*********** CONFIGURING TRAINING AlgorithmConfiguration **************
        //TODO make sure that the default options are set by initialization of the AlgorithmConfiguration Object
        //B) Algorithm config
        AlgorithmConfiguration algorithmConfiguration = null;
        /* 1st AlgorithmConfiguration CASE - algorithmId AND OPTIONALLY at least
            one Algorithm Option(algorithm options are crafted as a formatted String)
            - if none Algorithm Option provided, then the default will be set.
        */
        if(algorithmIdExist){
            Algorithm algorithm = algorithmRepository.findById(Integer.parseInt(algorithmId)).orElseThrow(() -> new EntityNotFoundException("The algorithm you provided could not be found."));
            algorithmConfiguration =  new AlgorithmConfiguration(algorithm);
            if(algorithmOptionsExist){
                algorithmConfiguration.setOptions(algorithmOptions);
            }
            algorithmConfiguration.setUser(user);
            algorithmConfiguration = algorithmConfigurationRepository.save(algorithmConfiguration);
        }

       /* 2nd AlgorithmConfiguration CASE - algorithmConfigurationId AND OPTIONALLY Options
            - if none Algorithm Option provided, then the Options of the current AlgorithmConfiguration will be set.
        */
        else if(algorithmConfigurationIdExist){
            algorithmConfiguration = algorithmConfigurationRepository.findById(Integer.parseInt(algorithmConfigurationId)).orElseThrow(() -> new EntityNotFoundException("The algorithm configuration you provided could not be found."));
            //TODO (!)CHECK WHY: Intellij warnings that "Condition 'algorithmOptionsExist' is always 'false'" while I am getting the options from the request.
            if(algorithmOptionsExist){
                algorithmConfiguration.setOptions(algorithmOptions);
            }
        }

        //*********** END OF CONFIGURING TRAINING AlgorithmConfiguration **************
        //TODO algorithmId and trainingId or modelID exists return error
        Training training = new Training();
        if(trainingIdExist || modelIdExist){

            if(trainingIdExist){
                training = trainingRepository.findById(Integer.parseInt(trainingId)).orElseThrow(()-> new EntityNotFoundException("The Dataset Configuration you provided could not be found."));

            }

            if(modelIdExist){
                training = trainingRepository.findByModel(modelRepository.findById(Integer.parseInt(modelId))
                        .orElseThrow(()-> new EntityNotFoundException("The Model you provided could not be found.")))
                        .orElseThrow(()-> new EntityNotFoundException("The Training of the Model you provided could not be found."));
            }
            //CASE 1: User gives new configure for the existing algorithmConfiguration
            if(!algorithmConfigurationIdExist){
                algorithmConfiguration = training.getAlgorithmConfiguration();
                if(algorithmOptionsExist){
                    algorithmConfiguration.setOptions(algorithmOptions);
                    algorithmConfiguration.setUser(user);
                    algorithmConfiguration = algorithmConfigurationRepository.save(algorithmConfiguration);
                }
            }

            if(!multipartFileExist && !datasetIdExist && !datasetConfigurationIdExist){
                datasetConfiguration = training.getDatasetConfiguration();
                if(basicCharacteristicsColumnsExist){
                    datasetConfiguration.setBasicAttributesColumns(basicCharacteristicsColumns);
                }
                if(targetClassColumnExist){
                    datasetConfiguration.setTargetColumn(targetClassColumn);
                }
            }
        }

        trainingDataInput.setDatasetConfiguration(datasetConfiguration);
        trainingDataInput.setFilename(datasetConfiguration.getDataset().getFileName());
        trainingDataInput.setAlgorithmConfiguration(algorithmConfiguration);
        training = trainingRepository.save(training);
        trainingDataInput.setTraining(training);

        if(multipartFileExist){
           trainingDataInput.setDataset(DatasetUtil.prepareDataset(file, datasetConfiguration.getDataset().getFileName(), datasetConfiguration));
        }
        return trainingDataInput;

    }



}
