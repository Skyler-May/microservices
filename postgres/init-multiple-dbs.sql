-- 创建业务数据库
CREATE DATABASE gaming_db;
CREATE DATABASE unipay_db;
CREATE DATABASE wanghu_db;
CREATE DATABASE temporal_db;
-- 添加更多库...

-- 授权 TO common
GRANT ALL PRIVILEGES ON DATABASE gaming_db TO common;
GRANT ALL PRIVILEGES ON DATABASE unipay_db TO common;
GRANT ALL PRIVILEGES ON DATABASE wanghu_db TO common;
GRANT ALL PRIVILEGES ON DATABASE temporal_db TO common;