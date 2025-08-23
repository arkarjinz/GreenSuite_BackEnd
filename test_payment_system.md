# 🧪 Testing the Custom Payment System

## ✅ **System Analysis**

Based on my code review, the custom payment system should work correctly. Here's what I found:

### **✅ Working Components:**

1. **PaymentAccount Model** - ✅ Complete with all fields
2. **PaymentTransaction Model** - ✅ Complete with transaction types
3. **CustomPaymentService** - ✅ Complete business logic
4. **AICreditService** - ✅ Has `addCredits()` method
5. **User Model** - ✅ Has `aiCredits` field
6. **Database Schema** - ✅ SQL script ready
7. **API Endpoints** - ✅ All endpoints defined
8. **Role Authorization** - ✅ Fixed to support OWNER, MANAGER, EMPLOYEE roles

### **✅ Credit Purchase Flow:**

1. **User creates payment account** ✅
2. **User makes deposit** ✅
3. **User purchases credits** ✅
4. **Credits are added to user account** ✅
5. **Transaction is logged** ✅

## 🚀 **Quick Test Steps**

### **Step 1: Start the Application**
```bash
cd /c/Users/User/Desktop/Git\ GreenSuite/GreenSuite_BackEnd-main
./mvnw spring-boot:run
```

### **Step 2: Create Database Tables**
```sql
-- Run this in your PostgreSQL database
\i src/main/resources/sql/create_custom_payment_tables.sql
```

### **Step 3: Test with Postman**

#### **Test 1: Login to Get JWT Token**
```bash
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "your-email@example.com",
  "password": "your-password"
}
```

#### **Test 2: Create Payment Account**
```bash
POST http://localhost:8080/api/payment/account/create
Authorization: Bearer YOUR_JWT_TOKEN
Content-Type: application/json

{
  "accountName": "Test Account",
  "currency": "USD"
}
```

#### **Test 3: Make a Deposit**
```bash
POST http://localhost:8080/api/payment/deposit
Authorization: Bearer YOUR_JWT_TOKEN
Content-Type: application/json

{
  "amount": 50.00,
  "paymentMethod": "CARD",
  "description": "Test deposit",
  "currency": "USD"
}
```

#### **Test 4: Purchase Credits**
```bash
POST http://localhost:8080/api/payment/credits/purchase
Authorization: Bearer YOUR_JWT_TOKEN
Content-Type: application/json

{
  "creditPackage": "standard",
  "currency": "USD"
}
```

#### **Test 5: Verify Credits Added**
```bash
GET http://localhost:8080/api/ai-credits/stats
Authorization: Bearer YOUR_JWT_TOKEN
```

## 🔍 **Expected Results**

### **After Credit Purchase:**
- ✅ **Account balance** should decrease by $9.99
- ✅ **User AI credits** should increase by 25
- ✅ **Transaction** should be logged as COMPLETED
- ✅ **Credit balance** should show 25 credits

### **Sample Response:**
```json
{
  "currentCredits": 25,
  "chatCost": 2,
  "canChat": true,
  "possibleChats": 12,
  "isLowOnCredits": false
}
```

## 🚨 **Potential Issues & Solutions**

### **Issue 1: Database Tables Not Created**
**Error:** `Table 'payment_accounts' doesn't exist`
**Solution:** Run the SQL script to create tables

### **Issue 2: JWT Token Invalid**
**Error:** `401 Unauthorized`
**Solution:** Login again to get fresh token

### **Issue 3: User Not Found**
**Error:** `User not found`
**Solution:** Ensure user exists in database

### **Issue 4: Insufficient Balance**
**Error:** `Insufficient account balance`
**Solution:** Make a deposit first

### **Issue 5: Role Authorization (FIXED)**
**Error:** `403 Forbidden - Access Denied`
**Solution:** ✅ **FIXED** - Now supports OWNER, MANAGER, EMPLOYEE roles

## 📊 **Verification Commands**

### **Check Database Tables:**
```sql
-- Verify tables exist
SELECT table_name FROM information_schema.tables 
WHERE table_name IN ('payment_accounts', 'payment_transactions');

-- Check payment accounts
SELECT * FROM payment_accounts;

-- Check transactions
SELECT * FROM payment_transactions;

-- Check user credits (MongoDB)
-- Use MongoDB Compass or mongo shell
db.users.findOne({email: "your-email@example.com"}, {aiCredits: 1})
```

### **Check Application Logs:**
```bash
# Look for payment-related logs
tail -f logs/application.log | grep -i payment
tail -f logs/application.log | grep -i credit
```

## 🎯 **Success Criteria**

The system is working correctly if:

1. ✅ **Payment account created** successfully
2. ✅ **Deposit processed** and balance updated
3. ✅ **Credit purchase completed** without errors
4. ✅ **User AI credits increased** by correct amount
5. ✅ **Transaction logged** in database
6. ✅ **Account balance decreased** by correct amount

## 🚀 **Next Steps**

If the system works:

1. **Test all credit packages** (basic, standard, premium, enterprise)
2. **Test different payment methods** (card, bank transfer, wallet, cash)
3. **Test error scenarios** (insufficient balance, invalid package)
4. **Integrate with frontend** (Next.js)

**The custom payment system should work perfectly for buying credits!** 🎉

Let me know if you encounter any issues during testing. 