-- Fix payments table schema to allow null values for account_number
-- This allows transaction records to not have an account number

-- First, drop the unique constraint on account_number
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_account_number_key;

-- Then allow null values for account_number
ALTER TABLE payments ALTER COLUMN account_number DROP NOT NULL;

-- Add a new unique constraint that only applies to non-null values
-- This ensures account numbers are unique when they exist, but allows nulls
CREATE UNIQUE INDEX idx_payments_account_number_unique 
ON payments(account_number) 
WHERE account_number IS NOT NULL;

-- Add a comment to document the change
COMMENT ON COLUMN payments.account_number IS 'Account number for payment accounts. NULL for transaction records.';

-- Verify the changes
SELECT column_name, is_nullable, data_type 
FROM information_schema.columns 
WHERE table_name = 'payments' AND column_name = 'account_number'; 