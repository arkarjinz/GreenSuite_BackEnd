-- Migration script to fix payments table for transaction records
-- This allows account_number to be NULL for transaction records while maintaining uniqueness for actual accounts

-- Step 1: Drop existing unique constraint if it exists
DO $$ 
BEGIN
    -- Drop unique constraint if it exists
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'payments_account_number_key' 
        AND table_name = 'payments'
    ) THEN
        ALTER TABLE payments DROP CONSTRAINT payments_account_number_key;
    END IF;
    
    -- Drop unique index if it exists
    IF EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE indexname = 'idx_payments_account_number_unique'
    ) THEN
        DROP INDEX idx_payments_account_number_unique;
    END IF;
END $$;

-- Step 2: Allow null values for account_number
ALTER TABLE payments ALTER COLUMN account_number DROP NOT NULL;

-- Step 3: Create a partial unique index that only applies to non-null values
-- This ensures account numbers are unique when they exist, but allows multiple nulls
CREATE UNIQUE INDEX idx_payments_account_number_unique 
ON payments(account_number) 
WHERE account_number IS NOT NULL;

-- Step 4: Add documentation
COMMENT ON COLUMN payments.account_number IS 'Account number for payment accounts. NULL for transaction records.';

-- Step 5: Verify the changes
SELECT 
    column_name, 
    is_nullable, 
    data_type,
    CASE 
        WHEN is_nullable = 'YES' THEN 'NULL values allowed'
        ELSE 'NULL values not allowed'
    END as null_status
FROM information_schema.columns 
WHERE table_name = 'payments' AND column_name = 'account_number';

-- Step 6: Show the new index
SELECT 
    indexname, 
    indexdef 
FROM pg_indexes 
WHERE tablename = 'payments' AND indexname = 'idx_payments_account_number_unique'; 