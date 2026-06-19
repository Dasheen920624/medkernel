-- 医学评测运行冻结当前部署制品指纹；历史空值不得用于当前制品放行。
ALTER TABLE mk_llm_eval_run ADD (release_fingerprint VARCHAR2(128) NULL);

COMMENT ON COLUMN mk_llm_eval_run.release_fingerprint IS '生成医学评测时冻结的运行制品指纹，必须与当前部署一致方可签署和放行';
