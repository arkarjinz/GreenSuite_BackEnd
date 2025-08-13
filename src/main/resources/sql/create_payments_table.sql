-- Create payments table for GreenSuite payment system
CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    user_name VARCHAR(255),
    user_id VARCHAR(255),
    account_number VARCHAR(255) UNIQUE NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    amount DECIMAL(10,2) DEFAULT 0.00,
    credit_points BIGINT DEFAULT 50,
    payment_method VARCHAR(50),
    transaction_reference VARCHAR(255),
    credits_purchased INTEGER,
    credit_package VARCHAR(50),
    stripe_payment_intent_id VARCHAR(255),
    stripe_customer_id VARCHAR(255)
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments(user_id);
CREATE INDEX IF NOT EXISTS idx_payments_account_number ON payments(account_number);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_created_date ON payments(created_date);
CREATE INDEX IF NOT EXISTS idx_payments_payment_method ON payments(payment_method);

-- Add comments for documentation
COMMENT ON TABLE payments IS 'Payment accounts and transactions for GreenSuite credit system';
COMMENT ON COLUMN payments.id IS 'Primary key for payment records';
COMMENT ON COLUMN payments.user_name IS 'Full name of the user';
COMMENT ON COLUMN payments.user_id IS 'User ID from the main user system';
COMMENT ON COLUMN payments.account_number IS 'Unique account number for payment operations';
COMMENT ON COLUMN payments.status IS 'Payment status: PENDING, COMPLETED, FAILED, REFUNDED';
COMMENT ON COLUMN payments.amount IS 'Current balance in the payment account';
COMMENT ON COLUMN payments.credit_points IS 'Credit points associated with the account';
COMMENT ON COLUMN payments.payment_method IS 'Method used for payment: CARD, BANK_TRANSFER, WALLET';
COMMENT ON COLUMN payments.transaction_reference IS 'Unique reference for payment transactions';
COMMENT ON COLUMN payments.credits_purchased IS 'Number of credits purchased in a transaction';
COMMENT ON COLUMN payments.credit_package IS 'Credit package type: basic, standard, premium, enterprise';
COMMENT ON COLUMN payments.stripe_payment_intent_id IS 'Stripe payment intent ID for tracking';
COMMENT ON COLUMN payments.stripe_customer_id IS 'Stripe customer ID for user'; 