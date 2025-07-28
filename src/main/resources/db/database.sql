select custom_algorithm_id from trainings where id=5;

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

select * from algorithm_configurations;

select * from trainings;

select * from users;
select * from algorithms;

DROP DATABASE "app-db";

select * from const_algorithm_types;
SELECT id, name FROM const_algorithm_types;

select * from const_model_types;

select * from users;

select * from algorithm_configurations;
DELETE FROM trainings
WHERE dataset_id IS NULL;

select * from models;

select * from algorithms
;

select * from categories;

select *
from trainings;


select * from dataset_configurations;
select * from models_executions;
select * from Datasets;

select *
from models;

select * from algorithms where class_name='weka.classifiers.functions.LinearRegression';

