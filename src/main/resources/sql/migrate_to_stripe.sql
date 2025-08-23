-- Migration script to update payments table for Stripe integration
-- Run this script to add Stripe-specific columns to existing payments table

-- Add Stripe-specific columns if they don't exist
DO $$ 
BEGIN
    -- Add stripe_payment_intent_id column if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'payments' AND column_name = 'stripe_payment_intent_id') THEN
        ALTER TABLE payments ADD COLUMN stripe_payment_intent_id VARCHAR(255);
    END IF;
    
    -- Add stripe_customer_id column if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'payments' AND column_name = 'stripe_customer_id') THEN
        ALTER TABLE payments ADD COLUMN stripe_customer_id VARCHAR(255);
    END IF;
END $$;

-- Update existing credit package values to lowercase (new format)
UPDATE payments 
SET credit_package = LOWER(credit_package) 
WHERE credit_package IN ('BASIC', 'STANDARD', 'PREMIUM');

-- Create index for Stripe payment intent ID for better performance
CREATE INDEX IF NOT EXISTS idx_payments_stripe_payment_intent_id ON payments(stripe_payment_intent_id);

-- Create index for Stripe customer ID for better performance
CREATE INDEX IF NOT EXISTS idx_payments_stripe_customer_id ON payments(stripe_customer_id);

-- Add comments for new columns
COMMENT ON COLUMN payments.stripe_payment_intent_id IS 'Stripe payment intent ID for tracking';
COMMENT ON COLUMN payments.stripe_customer_id IS 'Stripe customer ID for user';

-- Update table comment
COMMENT ON TABLE payments IS 'Payment accounts and transactions for GreenSuite credit system with Stripe integration'; 