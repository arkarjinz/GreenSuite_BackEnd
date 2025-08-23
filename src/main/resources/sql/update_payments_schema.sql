-- Update payments table to allow null values for account_number
-- This allows transaction records to not have an account number
ALTER TABLE payments ALTER COLUMN account_number DROP NOT NULL;

-- Add a comment to document the change
COMMENT ON COLUMN payments.account_number IS 'Account number for payment accounts. NULL for transaction records.'; 