
select * from trainings order by started_date desc;

select * from public.custom_algorithm_configurations where id=11;

select * from algorithm_parameters where configuration_id=12;

select accessibility_id from custom_algorithms where id=8;

select * from custom_algorithms;

select * from custom_algorithm_images where custom_algorithm_id=12;

select * from algorithm_parameters where algorithm_id=12;

select * from models order by finished_at desc;

select * from custom_algorithms where id=23;

select * from users where users.username='jbiden';

select * from categories;

select * from trainings order by started_date desc;

select * from category_requests;

SELECT * FROM users WHERE username = 'jbiden';

select * from jwt_tokens;

select * from models_executions;

select * from async_task_status;

select * from algorithms where class_name='weka.classifiers.functions.LinearRegression';

select * from trainings;

select * from users;
select * from algorithms;

DROP DATABASE "app-db";

select * from const_algorithm_types;
SELECT id, name FROM const_algorithm_types;

select * from const_model_types;

select * from users;

DELETE FROM trainings
WHERE dataset_id IS NULL;

select * from models;

select * from algorithms
;

select * from categories;

select * from trainings order by id asc;

select * from models where training_id=145;

select * from models_executions where model_id=97;


select * from dataset_configurations;
select * from models_executions;
select * from Datasets;

select *
from models;

select * from algorithms where class_name='weka.classifiers.functions.LinearRegression';

select * from users;

select * from custom_algorithms;

select * from custom_algorithm_configurations;

select * from dataset_configurations;

select * from trainings where id=142;


select * from models where training_id=142;


select * from custom_algorithms;

INSERT INTO trainings (
    started_date,
    finished_date,
    status_id,
    algorithm_configuration_id,
    custom_algorithm_configuration_id,
    user_id,
    dataset_id,
    results
)
VALUES
    (NOW(), NOW() + INTERVAL '1 minute', 1, 9, NULL, 1, 1, 'Test result 1'),
    (NOW(), NOW() + INTERVAL '2 minutes', 1, 1, NULL, 1, 1, 'Test result 2'),
    (NOW(), NOW() + INTERVAL '3 minutes', 1, 1, NULL, 1, 1, 'Test result 3'),
    (NOW(), NOW() + INTERVAL '4 minutes', 1, 1, NULL, 1, 1, 'Test result 4'),
    (NOW(), NOW() + INTERVAL '5 minutes', 1, 1, NULL, 1, 1, 'Test result 5');

ALTER TABLE async_task_status
    ADD COLUMN stop_requested BOOLEAN DEFAULT FALSE NOT NULL;

select * from async_task_status where task_id='f44f9086-6320-4d19-99d9-242dd5a11b30';
select * from trainings where id=145;

select * from models where training_id=145;

select * from users where username='dtrump';

select * from models where id=102;



select * from models_executions where model_id=97;
ALTER TABLE async_task_status
    DROP CONSTRAINT async_task_status_status_check;

ALTER TABLE async_task_status
    ADD CONSTRAINT async_task_status_status_check
        CHECK (
            (status)::text = ANY (
                ARRAY['PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'STOPPED']
                )
            );


select * from async_task_status;
