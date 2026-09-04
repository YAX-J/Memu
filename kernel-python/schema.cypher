-- Memu 知识图谱 DDL（Kùzu）
-- 所有表用 IF NOT EXISTS，可安全重复执行

CREATE NODE TABLE IF NOT EXISTS Person(
    id         STRING PRIMARY KEY,
    name       STRING,
    role       STRING,
    relation   STRING,
    confidence DOUBLE,
    createdAt  TIMESTAMP,
    updatedAt  TIMESTAMP
);

CREATE NODE TABLE IF NOT EXISTS Project(
    id         STRING PRIMARY KEY,
    name       STRING,
    status     STRING,
    confidence DOUBLE,
    createdAt  TIMESTAMP,
    updatedAt  TIMESTAMP
);

CREATE NODE TABLE IF NOT EXISTS Task(
    id         STRING PRIMARY KEY,
    title      STRING,
    status     STRING,
    dueAt      TIMESTAMP,
    confidence DOUBLE,
    createdAt  TIMESTAMP,
    updatedAt  TIMESTAMP
);

CREATE NODE TABLE IF NOT EXISTS Topic(
    id         STRING PRIMARY KEY,
    name       STRING,
    confidence DOUBLE,
    createdAt  TIMESTAMP,
    updatedAt  TIMESTAMP
);

CREATE NODE TABLE IF NOT EXISTS Event(
    id         STRING PRIMARY KEY,
    type       STRING,
    source     STRING,
    rawText    STRING,
    occurredAt TIMESTAMP,
    confidence DOUBLE,
    createdAt  TIMESTAMP,
    updatedAt  TIMESTAMP
);

CREATE NODE TABLE IF NOT EXISTS Preference(
    id         STRING PRIMARY KEY,
    key        STRING,
    value      STRING,
    weight     DOUBLE,
    createdAt  TIMESTAMP,
    updatedAt  TIMESTAMP
);

CREATE NODE TABLE IF NOT EXISTS Habit(
    id            STRING PRIMARY KEY,
    pattern       STRING,
    period        STRING,
    confidence    DOUBLE,
    feedbackScore DOUBLE,
    nextAt        TIMESTAMP,
    muted         BOOLEAN,
    dismissStreak INT64,
    createdAt     TIMESTAMP,
    updatedAt     TIMESTAMP
);

CREATE REL TABLE IF NOT EXISTS PARTICIPATES_IN(FROM Person TO Event, weight DOUBLE, createdAt TIMESTAMP);
CREATE REL TABLE IF NOT EXISTS HAS_PREFERENCE(FROM Person TO Preference, weight DOUBLE, updatedAt TIMESTAMP);
CREATE REL TABLE IF NOT EXISTS FOLLOWS(FROM Person TO Habit, weight DOUBLE, updatedAt TIMESTAMP);
CREATE REL TABLE IF NOT EXISTS RELATES_TO(FROM Event TO Topic, weight DOUBLE, createdAt TIMESTAMP);
CREATE REL TABLE IF NOT EXISTS BELONGS_TO(FROM Task TO Project, createdAt TIMESTAMP);
CREATE REL TABLE IF NOT EXISTS PRECEDES(FROM Event TO Event, gapSeconds DOUBLE);

-- 说明：RELATES_TO 目前只声明到 Topic。后续若需指向 Project / Task，
-- 需拆成 RELATES_TOPIC / RELATES_PROJECT / RELATES_TASK 三条边
-- （Kùzu 的边必须声明固定的 FROM/TO 表，不支持多态终点）。
