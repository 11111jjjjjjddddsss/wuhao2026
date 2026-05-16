ALTER TABLE support_messages
  ADD COLUMN IF NOT EXISTS image_urls_json JSON NULL AFTER body;
