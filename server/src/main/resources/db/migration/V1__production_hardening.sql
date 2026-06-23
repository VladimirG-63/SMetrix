CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE material_cache ADD COLUMN IF NOT EXISTS popularity_score integer NOT NULL DEFAULT 0;

DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE c.contype = 'f'
          AND n.nspname = current_schema()
          AND (
              (t.relname = 'projects' AND EXISTS (
                  SELECT 1
                  FROM unnest(c.conkey) AS k(attnum)
                  JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
                  WHERE a.attname = 'user_id'
              )) OR
              (t.relname = 'project_rooms' AND EXISTS (
                  SELECT 1
                  FROM unnest(c.conkey) AS k(attnum)
                  JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
                  WHERE a.attname = 'project_id'
              )) OR
              (t.relname = 'openings' AND EXISTS (
                  SELECT 1
                  FROM unnest(c.conkey) AS k(attnum)
                  JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
                  WHERE a.attname = 'project_room_id'
              )) OR
              (t.relname = 'estimate_items' AND EXISTS (
                  SELECT 1
                  FROM unnest(c.conkey) AS k(attnum)
                  JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
                  WHERE a.attname = 'project_room_id'
              )) OR
              (t.relname = 'workers' AND EXISTS (
                  SELECT 1
                  FROM unnest(c.conkey) AS k(attnum)
                  JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
                  WHERE a.attname = 'user_id'
              )) OR
              (t.relname = 'work_tasks' AND EXISTS (
                  SELECT 1
                  FROM unnest(c.conkey) AS k(attnum)
                  JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
                  WHERE a.attname IN ('project_room_id', 'worker_id')
              ))
          )
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', (
            SELECT t.relname
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            WHERE c.conname = constraint_name
        ), constraint_name);
    END LOOP;
END $$;

-- Client-facing IDs stay JSON strings, but PostgreSQL stores and indexes native UUID values.
ALTER TABLE users ALTER COLUMN id TYPE uuid USING id::uuid;
ALTER TABLE projects ALTER COLUMN id TYPE uuid USING id::uuid;
ALTER TABLE projects ALTER COLUMN user_id TYPE uuid USING user_id::uuid;
ALTER TABLE project_rooms ALTER COLUMN id TYPE uuid USING id::uuid;
ALTER TABLE project_rooms ALTER COLUMN project_id TYPE uuid USING project_id::uuid;
ALTER TABLE openings ALTER COLUMN id TYPE uuid USING id::uuid;
ALTER TABLE openings ALTER COLUMN project_room_id TYPE uuid USING project_room_id::uuid;
ALTER TABLE estimate_items ALTER COLUMN id TYPE uuid USING id::uuid;
ALTER TABLE estimate_items ALTER COLUMN project_room_id TYPE uuid USING project_room_id::uuid;
ALTER TABLE workers ALTER COLUMN id TYPE uuid USING id::uuid;
ALTER TABLE workers ALTER COLUMN user_id TYPE uuid USING user_id::uuid;
ALTER TABLE work_tasks ALTER COLUMN id TYPE uuid USING id::uuid;
ALTER TABLE work_tasks ALTER COLUMN project_room_id TYPE uuid USING project_room_id::uuid;
ALTER TABLE work_tasks ALTER COLUMN worker_id TYPE uuid USING worker_id::uuid;
ALTER TABLE material_cache ALTER COLUMN id TYPE uuid USING id::uuid;
ALTER TABLE unit_conversions ALTER COLUMN id TYPE uuid USING id::uuid;
ALTER TABLE revoked_token ALTER COLUMN token TYPE uuid USING token::uuid;

CREATE INDEX IF NOT EXISTS idx_material_cache_name_trgm
    ON material_cache USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_material_cache_code_trgm
    ON material_cache USING gin (code gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_projects_user_id ON projects(user_id);
CREATE INDEX IF NOT EXISTS idx_estimate_items_room_id ON estimate_items(project_room_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_projects_user') THEN
        ALTER TABLE projects ADD CONSTRAINT fk_projects_user FOREIGN KEY (user_id) REFERENCES users(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_rooms_project') THEN
        ALTER TABLE project_rooms ADD CONSTRAINT fk_rooms_project FOREIGN KEY (project_id) REFERENCES projects(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_openings_room') THEN
        ALTER TABLE openings ADD CONSTRAINT fk_openings_room FOREIGN KEY (project_room_id) REFERENCES project_rooms(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_estimates_room') THEN
        ALTER TABLE estimate_items ADD CONSTRAINT fk_estimates_room FOREIGN KEY (project_room_id) REFERENCES project_rooms(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_workers_user') THEN
        ALTER TABLE workers ADD CONSTRAINT fk_workers_user FOREIGN KEY (user_id) REFERENCES users(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tasks_room') THEN
        ALTER TABLE work_tasks ADD CONSTRAINT fk_tasks_room FOREIGN KEY (project_room_id) REFERENCES project_rooms(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tasks_worker') THEN
        ALTER TABLE work_tasks ADD CONSTRAINT fk_tasks_worker FOREIGN KEY (worker_id) REFERENCES workers(id);
    END IF;
END $$;
