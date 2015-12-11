package commons.scripts

import java.text.DecimalFormat
import java.util.Map

import com.braintreegateway.BraintreeGateway
import com.braintreegateway.ClientTokenRequest
import com.braintreegateway.CreditCard as BTCreditCard
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.Customer as BTCustomer
import com.braintreegateway.Environment
import com.braintreegateway.PaymentMethod as BTPaymentMethod
import com.braintreegateway.PaymentMethodRequest;
import com.braintreegateway.Plan as BTPlan
import com.braintreegateway.ResourceCollection;
import com.braintreegateway.Result
import com.braintreegateway.Subscription;
import com.braintreegateway.SubscriptionSearchRequest;
import com.braintreegateway.Transaction
import com.braintreegateway.TransactionAddressRequest
import com.braintreegateway.TransactionRequest
import com.braintreegateway.TransactionSearchRequest;
import com.braintreegateway.Subscription.Status
import com.vitalai.domain.commerce.CreditCard
import com.vitalai.domain.commerce.Customer
import com.vitalai.domain.commerce.Invoice
import com.vitalai.domain.commerce.PaymentInfo;
import com.vitalai.domain.commerce.PaymentMethod
import com.vitalai.domain.commerce.Plan
import com.vitalai.domain.commerce.ShippingInfo;

import ai.vital.domain.Login;
import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultElement
import ai.vital.vitalservice.query.ResultList;;
import ai.vital.vitalsigns.model.VITAL_Node
import ai.vital.vitalsigns.model.VitalApp

class BraintreeIntegrationScript implements VitalPrimeGroovyScript {

	public final static vital_customer_id = "vital_customer_id"
	
	public final static vital_invoice_uri = "vital_invoice_uri"
	
	static DecimalFormat priceFormat = new DecimalFormat("0.00")
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
			
			String action = params.get('action')
			if(!action) throw new Exception("No action param")
			
			Map<String, Object> braintreeCfg = params.get("braintree")
			if(braintreeCfg == null) throw new Exception("No braintree param")
			
			BraintreeGateway gateway = initGateway(braintreeCfg)
			
			
			if(action == 'createCustomer') {
				
				Customer customer = params.get('customer')
				if(customer == null) throw new Exception("No customer param")
				String email = params.get('email')
				
				createCustomer(gateway, customer, email, rl)
				
			} else if(action == 'deleteCustomer') {
			
				String braintreeCustomerID = params.get('braintreeCustomerID')
			
				if(!braintreeCustomerID) throw new Exception("No braintreeCustomerID param")
			
				deleteCustomer(gateway, params, rl)
				
			} else if(action == 'saleTransaction') {
			
				Customer customer = params.get('customer')
				if(customer == null) throw new Exception("No customer param")
				
				Invoice invoice = params.get('invoice')
				if(invoice == null) throw new Exception("No invoice param")
			
				ShippingInfo shippingInfo = params.get('shippingInfo')
				if(shippingInfo == null) throw new Exception("No shippingInfo param")
				
				PaymentInfo paymentInfo = params.get('paymentInfo')
				if(paymentInfo == null) throw new Exception("No paymentInfo param")
				
				String payment_method_nonce = params.get('payment_method_nonce')
				if(!payment_method_nonce) throw new Exception("No 'payment_method_nonce' param")
				
				saleTransaction(gateway, customer, invoice, shippingInfo, paymentInfo, payment_method_nonce, rl)
				
			} else if(action == 'generateClientToken') {
			
				Customer customer = params.get('customer')
				if(customer == null) throw new Exception("No customer param")
			
				ClientTokenRequest ctr = new ClientTokenRequest()
				if(customer.braintreeCustomerID) ctr.customerId(customer.braintreeCustomerID.toString())
	
				String token = gateway.clientToken().generate(ctr)
			
				VITAL_Node tokenNode = new VITAL_Node()
				tokenNode.generateURI((VitalApp) null)
				tokenNode.name = token
				rl.results.add(new ResultElement(tokenNode, 1D))
				
				rl.status = VitalStatus.withOKMessage("Token generated")
				
			} else if(action == 'getPlans') {
			
				getPlans(gateway, rl)
				
			} else if(action == 'getPaymentMethods') {
			
				Customer customer = params.get('customer')
				if(customer == null) throw new Exception("No customer param")
				
				getPaymentMethods(gateway, customer, rl)
				
			} else if(action == 'addPaymentMethod') {
			
				Customer customer = params.get('customer')
				if(customer == null) throw new Exception("No customer param")
				
				
				String payment_method_nonce = params.get('payment_method_nonce')
				if(!payment_method_nonce) throw new Exception("No 'payment_method_nonce' param")

				addPaymentMethod(gateway, customer, payment_method_nonce, rl)
				
			} else if(action == 'removePaymentMethod') {
			
				Customer customer = params.get('customer')
				if(customer == null) throw new Exception("No customer param")
				
				String token = params.get('token')
				if(!token)  throw new Exception("No 'token' param")
				
				removePaymentMethod(gateway, customer, token, rl)
			
			} else {
			
				throw new RuntimeException("Unknown action: ${action}")
				
			}
			
			
		} catch(Exception e) {
		
			rl.status = VitalStatus.withError(e.localizedMessage)
		
		}
		
		return rl;
	}
	
	void addPaymentMethod(BraintreeGateway gateway, Customer customer, String payment_method_nonce, ResultList rl) {
		
		if(!customer.braintreeCustomerID) throw new Exception("Customer has no braintreeCustomerID")
		
		PaymentMethodRequest pmr = new PaymentMethodRequest()
		pmr.customerId(customer.braintreeCustomerID.toString())
		pmr.paymentMethodNonce(payment_method_nonce)
		
		pmr.options().failOnDuplicatePaymentMethod(true)
		
		Result<? extends BTPaymentMethod> result = gateway.paymentMethod().create(pmr)
		
		if(!result.isSuccess()) {
			rl.status = VitalStatus.withError(result.getMessage())
			return
		}
		
		PaymentMethod pm = btPaymentMethodToPaymentMethod(result.getTarget())
		
		rl.results.add(new ResultElement(pm, 1D))
		
	}
	
	void removePaymentMethod(BraintreeGateway gateway, Customer customer, String token, ResultList rl) {
		
		BTPaymentMethod currentPM = gateway.paymentMethod().find(token)
		if(currentPM == null) {
			rl.status = VitalStatus.withError("Payment method not found")
			return
		}
		
		if(!currentPM.getCustomerId().equals(customer.braintreeCustomerID.toString())) {
			rl.status = VitalStatus.withError("Payment method does not belong to this customer")
			return
		}
		
		TransactionSearchRequest tsr = new TransactionSearchRequest()
		tsr.customerId().contains(customer.braintreeCustomerID.toString())
		
		ResourceCollection<Transaction> txs = gateway.transaction().search(tsr)
		
		List<String> subscriptionIDs = new ArrayList<String>()
		
		for(Transaction tx : txs) {
			
			String sid = tx.getSubscriptionId();
			if(sid && !subscriptionIDs.contains(sid)) subscriptionIDs.add(sid)
			
		}
		
		if(subscriptionIDs.size() > 0) {
			
			SubscriptionSearchRequest ssr = new SubscriptionSearchRequest()
			ssr.ids().in(subscriptionIDs)
//			gateway.subscription().search();
			ResourceCollection<Subscription> subs = gateway.subscription().search(ssr)
			
			for(Subscription sub : subs) {
				
				Status status = sub.getStatus()
				
				if( token.equals(sub.getPaymentMethodToken()) && !(status == Status.CANCELED || status == Status.EXPIRED) ) {
					rl.status = VitalStatus.withError("Cannot remove this payment method. It's used in a subscription with status: ${status.name()}" )
					return
				}
				
//				ifsub.getPaymentMethodToken()
				
			}
			
		}
		
		
		Result<? extends BTPaymentMethod> result = gateway.paymentMethod().delete(token)
		
		if(!result.isSuccess()) {
			
			rl.status = VitalStatus.withError("Couldn't remove payment method: " + result.getMessage())
			
		} else {
		
			rl.status = VitalStatus.withOKMessage("Payment method removed successfully")
			
		}
		
	}
	
	private PaymentMethod btPaymentMethodToPaymentMethod(BTPaymentMethod btpm) {
		
		PaymentMethod pm = null
		
		if(btpm instanceof BTCreditCard) {
			
			pm = new CreditCard()
			
			pm.cardType = btpm.cardType
			pm.expirationDate = btpm.getExpirationMonth() + "/" + btpm.getExpirationYear()
			pm.maskedNumber = btpm.getMaskedNumber()
			pm.name = btpm.cardType
			
		} else {
		
			//other methods stored
			pm = new PaymentMethod()
			
			pm.name = btpm.getClass().simpleName
		
		}
		
		pm.token = btpm.getToken()
		
		pm.generateURI((VitalApp) null)
		
		return pm
		
	}
	
	void getPaymentMethods(BraintreeGateway gateway, Customer customer, ResultList rl) {
		
		if(!customer.braintreeCustomerID) throw new Exception("Customer has no braintreeCustomerID")
		
		BTCustomer btCustomer = gateway.customer().find(customer.braintreeCustomerID.toString())
		
		if(btCustomer == null) throw new Exception("Braintree customer not found: ${customer.braintreeCustomerID}")
		
		List<? extends BTPaymentMethod> methods = btCustomer.getPaymentMethods()
		
		rl.totalResults = methods.size()
		
		for(BTPaymentMethod btpm : methods) {
			
			PaymentMethod pm = btPaymentMethodToPaymentMethod(btpm)
			
			rl.results.add(new ResultElement(pm, 1D))
			
		}
		
	}
	
	void getPlans(BraintreeGateway gateway, ResultList rl) {
		
		List<BTPlan> plans = gateway.plan().all()
		
		for( BTPlan plan : plans ) {

			Plan cp = new Plan()
			cp.generateURI(null, plan.getId())
			cp.billingDayOfMonth = plan.getBillingDayOfMonth()
			cp.billingFrequency = plan.getBillingFrequency()
			cp.braintreePlanID = plan.getId()
			cp.createdAt = plan.getCreatedAt()?.getTime()
			cp.currencyIsoCode = plan.getCurrencyIsoCode()
			cp.description = plan.getDescription()
			cp.name = plan.getName()
			cp.numberOfBillingCycles = plan.getNumberOfBillingCycles()
			cp.price = plan.getPrice()?.floatValue()
			cp.trialPeriod = plan.hasTrialPeriod()
			cp.trialDuration = plan.getTrialDuration()
			cp.trialDurationUnit = plan.getTrialDurationUnit()?.name()
			cp.updatedAt = plan.getUpdatedAt()?.getTime()
								
			rl.results.add(new ResultElement(cp, 1D))
			
		}
		
		rl.status = VitalStatus.withOKMessage("Plans listed, size: ${plans.size()}")
		
		rl.totalResults = plans.size()
		
	}
	
	void deleteCustomer(BraintreeGateway gateway, String braintreeCustomerID, ResultList rl) {
		
		Result<BTCustomer> result = gateway.customer().delete(braintreeCustomerID);
		
		if( ! result.isSuccess() ) {
			
			rl.status = VitalStatus.withError(result.getMessage())
			
		} else {
		
			rl.status = VitalStatus.withOKMessage("Braintree customer deleted, ID: ${braintreeCustomerID}")
		
		}
		
	}
	
	void createCustomer(BraintreeGateway gateway, Customer customer, String email, ResultList rl) {
		
		CustomerRequest btRequest = new CustomerRequest()
		if(customer.businessName) btRequest.company(customer.businessName.toString())
		if(email) btRequest.email(email)
		
		if(customer.name) btRequest.lastName(customer.name.toString())
		
		if(customer.mobilePhone) btRequest.phone(customer.mobilePhone.toString())
		if(customer.businessURL) btRequest.website(customer.businessURL.toString())
		
		btRequest.customField(vital_customer_id, customer.customerID.toString())
		
		Result<BTCustomer> result = gateway.customer().create(btRequest)
		
		if(!result.isSuccess()) {
			
			rl.status = VitalStatus.withError(result.getMessage())
			
		} else {
		
			//on successful account creation
			BTCustomer btCustomer = result.getTarget()
			
			customer.braintreeCustomerID = btCustomer.getId()
			
			rl.status = VitalStatus.withOKMessage("Braintree customer created, ID: ${btCustomer.getId()}")
			rl.results.add(new ResultElement(customer, 1D))
		
		}
		
	}
	
	void saleTransaction(BraintreeGateway gateway, Customer customer, Invoice invoice, ShippingInfo shippingInfo, PaymentInfo paymentInfo, String payment_method_nonce, ResultList rl) {
	
		Float grandTotal = invoice.grandTotal.floatValue()
		
		TransactionRequest request = new TransactionRequest()
		.customField(vital_customer_id, customer.customerID.toString())
		.customField(vital_invoice_uri, invoice.URI)
		.amount(new BigDecimal(priceFormat.format(grandTotal)))
		.paymentMethodNonce(payment_method_nonce )
	
		if(customer.braintreeCustomerID) request.customerId(customer.braintreeCustomerID.toString())
	
		request.orderId = invoice.URI
			
		String streetAddress = ""
		if(shippingInfo.streetAddress1) {
			streetAddress = shippingInfo.streetAddress1.toString()
		}
		
		if(shippingInfo.streetAddress2) {
			if(streetAddress.length() > 0) streetAddress += " \n"
			streetAddress += shippingInfo.streetAddress2.toString()
		}
		
	
		TransactionAddressRequest address = request.shippingAddress()
		if(streetAddress.length() > 0) {
			address.streetAddress(streetAddress)
		}
		
		if(shippingInfo.zipCode) {
			address.postalCode(shippingInfo.zipCode.toString())
		}
		
		if(shippingInfo.state) {
			address.region(shippingInfo.state.toString())
		}
		
		if(shippingInfo.city) {
			address.locality(shippingInfo.city.toString())
		}
		
		if(shippingInfo.fullName) {
			address.lastName(shippingInfo.fullName.toString())
		}
		
		Result<Transaction> result = gateway.transaction().sale(request);
	
		if(!result.isSuccess())	 {
			
			rl.status = VitalStatus.withError(result.getMessage())
			
		 } else {
		 
			 Transaction braintreeTransaction = result.getTarget()
		 
			 paymentInfo.braintreeTransactionID = braintreeTransaction.getId()
			paymentInfo.paymentStatus = 'done'
			
			rl.status = VitalStatus.withOKMessage("Transaction complete, ID: ${braintreeTransaction.getId()}")
			
			rl.results.add(new ResultElement(paymentInfo, 1D))
			
		 }
			
	}
	
	
	BraintreeGateway initGateway(Map<String, Object> braintreeCfg) {
		
		String btEnvironment = braintreeCfg.get('environment')
		if(!btEnvironment) throw new RuntimeException("No braintree.environment param")
		
		String btMerchantID = braintreeCfg.get('merchantID')
		if(!btMerchantID) throw new RuntimeException("No braintree.merchantID param")
		
		String btPublicKey = braintreeCfg.get('publicKey')
		if(!btPublicKey) throw new RuntimeException("No braintree.publicKey param")
		
		String btPrivateKey = braintreeCfg.get('privateKey')
		if(!btPrivateKey) throw new RuntimeException("No braintree.privateKey param")
		
		Environment env = null
		if("DEVELOPMENT".equalsIgnoreCase(btEnvironment)) {
			env = Environment.DEVELOPMENT
		} else if("SANDBOX".equalsIgnoreCase(btEnvironment)) {
			env = Environment.SANDBOX
		} else if("PRODUCTION".equalsIgnoreCase(btEnvironment)) {
			env = Environment.PRODUCTION
		} else {
			throw new RuntimeException("Unknown Braintree environment: ${btEnvironment}")
		}
		 
		BraintreeGateway braintreeGateway = new BraintreeGateway(
			env,
			btMerchantID,
			btPublicKey,
			btPrivateKey
		);
	
		return braintreeGateway
		
	}

}
