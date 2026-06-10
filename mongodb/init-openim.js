// Open-IM 数据库和用户初始化
db = db.getSiblingDB("openim_v3");
if (!db.getUser("openIM")) {
  db.createUser({
    user: "openIM",
    pwd: "openIM123",
    roles: [{ role: "readWrite", db: "openim_v3" }]
  });
  print("Open-IM user created successfully");
} else {
  print("Open-IM user already exists");
}
