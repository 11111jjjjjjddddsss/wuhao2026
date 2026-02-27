CREATE TABLE IF NOT EXISTS orders (
  order_id VARCHAR(128) PRIMARY KEY,
  user_id VARCHAR(128) NOT NULL,
  type ENUM('buy_topup','upgrade_plus_to_pro','renew_plus','renew_pro') NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  created_at BIGINT NOT NULL,
  status ENUM('success','failed') NOT NULL DEFAULT 'success',
  result_json JSON NULL
);

