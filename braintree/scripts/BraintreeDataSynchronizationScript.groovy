package commons.scripts

import java.util.Map
import java.util.Map.Entry

import org.joda.time.PeriodType;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.CreditCard as BTCreditCard
import com.braintreegateway.Customer as BTCustomer;
import com.braintreegateway.PaymentMethod as BTPaymentMethod;
import com.braintreegateway.Subscription
import com.braintreegateway.Transaction
import com.braintreegateway.exceptions.NotFoundException;
import com.vitalai.domain.commerce.Customer
import com.vitalai.domain.commerce.Edge_hasInvoice
import com.vitalai.domain.commerce.Edge_hasItem
import com.vitalai.domain.commerce.Edge_hasPaymentInfo;
import com.vitalai.domain.commerce.Edge_hasPaymentMethod;
import com.vitalai.domain.commerce.Edge_hasPlan
import com.vitalai.domain.commerce.Edge_hasSelectedPaymentMethod
import com.vitalai.domain.commerce.Edge_hasServiceContract;
import com.vitalai.domain.commerce.Edge_hasSubscription
import com.vitalai.domain.commerce.Invoice;
import com.vitalai.domain.commerce.InvoiceItem
import com.vitalai.domain.commerce.PaymentInfo;
import com.vitalai.domain.commerce.PaymentMethod;
import com.vitalai.domain.commerce.Plan
import com.vitalai.domain.commerce.Plan_PropertiesHelper;
import com.vitalai.domain.commerce.ServiceContract;

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.query.querybuilder.VitalBuilder;
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultElement
import ai.vital.vitalservice.query.ResultList;;
import ai.vital.vitalservice.query.VitalGraphQuery
import ai.vital.vitalservice.query.VitalSelectQuery;
import ai.vital.vitalsigns.block.CompactStringSerializer
import ai.vital.vitalsigns.meta.GraphContext;
import ai.vital.vitalsigns.model.GraphMatch
import ai.vital.vitalsigns.model.GraphObject
import ai.vital.vitalsigns.model.VITAL_Container
import ai.vital.vitalsigns.model.VitalApp;
import ai.vital.vitalsigns.model.VitalSegment
import ai.vital.vitalsigns.model.property.IProperty
import ai.vital.vitalsigns.model.property.StringProperty
import ai.vital.vitalsigns.model.property.URIProperty;

class BraintreeDataSynchronizationScript implements VitalPrimeGroovyScript {

	static int DAYS_PAST_DUE_GRACE = 7
	
	static GraphContext ContainerCtx = GraphContext.Container
	
	static VitalBuilder builder = new VitalBuilder()
	
	VitalPrimeScriptInterface scriptInterface = null
	
	VitalApp app = null
	
	BraintreeGateway gateway = null;
	
	Customer customer = null;
	
	VitalSegment customersSegment
	
	VitalSegment productsSegment
	
	VITAL_Container serviceContractsContainer = null
	
	Map<String, Subscription> btSubscriptionsMap = [:]
	
	List<String> warnings = []
	
//	VITAL_Container paymentMethodsContainer = null
//	
//	Map<String, PaymentMethod> token2VitalPaymentMethod = [:]
	
	Map<String, PaymentMethod> paymentMethodsMap = [:]
	
	int updatedInsertedObjects = 0
	
	int deletedObjects = 0
	
	boolean customerUpdated = false
	
	static boolean dryrun = false;
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> params) {

		this.scriptInterface = scriptInterface
		this.app = scriptInterface.getApp()
	
		ResultList rl = new ResultList()
		
		try {
			
			Map<String, Object> braintreeCfg = params.get("braintree")
			if(braintreeCfg == null) throw new Exception("No braintree param")
			
			Customer customer = params.get('customer')
			if(customer == null) throw new Exception("No customer param")
			
			String productsSegmentID = params.get('productsSegment')
			if(productsSegmentID == null) throw new Exception("No productsSegment segment")
			
			String customersSegmentID = params.get('customersSegment')
			if(customersSegmentID == null) throw new Exception("No customersSegment param")
			
			this.customer = customer
			
			this.gateway = BraintreeIntegrationScript.initGateway(braintreeCfg)
			
			List<VitalSegment> segments = scriptInterface.listSegments()
			
			for(VitalSegment s : segments) {
				if(s.segmentID.toString().equals(productsSegmentID)) {
					this.productsSegment = s
				} 
				if(s.segmentID.toString().equals(customersSegmentID)) {
					this.customersSegment = s
				}
			}
			
			if(productsSegment == null) {
				throw new Exception("No products segment found: ${productsSegmentID}");
			}
			
			if(customersSegment == null) {
				throw new Exception("No customers segment found: ${customersSegmentID}");
			}
			
			
			collectPaymentMethods()
			
			collectSubscriptionsList()
			
			collectBraintreeData(); 	
			
			synchronizeSubscriptions()		
			
			rl.status = VitalStatus.withOKMessage("Customer status updated ? ${customerUpdated}, objects updated/inserted: ${updatedInsertedObjects}, deleted: ${deletedObjects}, warnings [${warnings.size()}]: ${warnings}")
			rl.results.add(new ResultElement(customer, 1D))
			
		} catch(Exception e) {
		
			e.printStackTrace()
		
			rl.status = VitalStatus.withError(e.localizedMessage)
		
		}
		
		return rl;
	}
	
	void collectPaymentMethods() {
		
		VitalGraphQuery vgq = builder.query {
			
			GRAPH {
				
				value segments: [customersSegment]
				
				value offset : 0
				
				value limit: 1000
				
				value inlineObjects: true
				
				ARC {
					
					node_constraint { "URI eq ${customer.URI}"}
					
					ARC {
						
						edge_constraint { Edge_hasPaymentMethod.class }
						
					}
					
					
				}
				
			}
			
		}.toQuery()
		
		ResultList rl = scriptInterface.query(vgq)
		
		if(rl.status.status != VitalStatus.Status.ok) throw new Exception("Error when querying for exiting payment methods: " + rl.status.message)
		
		rl = unpackGraphMatch(rl)
		
		for(PaymentMethod pm : rl.iterator(PaymentMethod.class, false)) {
			paymentMethodsMap.put(pm.token.toString(), pm)
		}
		
		
	}
	
	void collectSubscriptionsList() {
		
		VitalGraphQuery vgq = builder.query {
			
			GRAPH {
				
				value segments: segmentsList([customersSegment, productsSegment])
				
				value limit: 1000
				
				value offset: 0
				
				value inlineObjects: true
				
				ARC {
					
					node_constraint { "URI eq ${customer.URI}" }
					
					ARC {
						
						edge_constraint { Edge_hasSubscription.class }
						
						node_constraint { ServiceContract.class }
						
						
						ARC {
							
							edge_constraint { Edge_hasPlan.class }
							
							node_constraint { Plan.class }
							
						}
						
						ARC {
							
							value optional: true
							
							edge_constraint { Edge_hasSelectedPaymentMethod.class }
							
						}
						
					}
					
				}
				
			}
			
		}.toQuery()
		
		ResultList rl = scriptInterface.query(vgq)
		
		if(rl.status.status != VitalStatus.Status.ok) throw new Exception("Error when querying for exiting services: " + rl.status.message)
		
		rl = unpackGraphMatch(rl)
		
		serviceContractsContainer = new VITAL_Container()
		
		for(GraphObject g : rl) {
			serviceContractsContainer.putGraphObject(g)
		}
		
	}

	void collectBraintreeData() {
		
		//synchronize payment methods
		
		BTCustomer btCustomer = null
		
		try {
			btCustomer = gateway.customer().find(customer.braintreeCustomerID.toString())
		} catch(NotFoundException e) {
		}
		
		if(btCustomer == null) throw new Exception("Customer not found in braintree")
		
		List<? extends BTPaymentMethod> methods = btCustomer.getPaymentMethods()
		
		BTPaymentMethod currentPM = null
		
		Subscription subscription = null
		
		BTPaymentMethod newMethod = null
		
		BTPaymentMethod currentMethod = null
		
//		Map<String, BTPaymentMethod> btPaymentMethodsMap = [:]
//		
//		Map<String, List<String>> paymentMethod2Subscriptions = [:]
		
		for(BTPaymentMethod btpm : methods) {
			
			if(btpm instanceof BTCreditCard) {
	
				BTCreditCard btCC = btpm
			
				for( Subscription sub : btCC.getSubscriptions() ) {

					btSubscriptionsMap[sub.getId()] = sub
					
				}
				
			}
			
		}
		
		
	}
	
	void synchronizeSubscriptions() {
		
		Set<String> checkedBTSubs = new HashSet<String>()
		
		ServiceContract activeContract = null
		
		for(ServiceContract sc : serviceContractsContainer.iterator(ServiceContract.class, false)) {
			
			String subscriptionID = sc.braintreeSubscriptionID.toString()
			
			Subscription btSubscription = btSubscriptionsMap[subscriptionID];
		
			if(btSubscription == null) {
				
				warnings.add("Subscription not found on the braintree side: ${subscriptionID}, URI: ${sc.URI} - consider removing it")
				
				continue
				
			}
			
			checkedBTSubs.add(btSubscription.getId())
			
			List<Plan> plans = sc.getPlans(ContainerCtx, serviceContractsContainer)
			if(plans.size() == 0) {
				warnings.add("Subscription not connected to a plan: ${subscriptionID}, URI: ${sc.URI}")
				continue;
			}
			
			sc = synchronizeSubscription(sc, plans.get(0), btSubscription)
			
			String status = sc.subscriptionStatus?.toString()
			
			if('ACTIVE' == status || 'PAST_DUE' == status || 'PENDING' == status) {
				if(activeContract != null) {
					warnings.add("More than 1 active contract found: ${sc.braintreeSubscriptionID} - ${status} ,  ${activeContract.braintreeSubscriptionID} - ${activeContract.subscriptionStatus}")
				}
				activeContract = sc
			}
				
		}
		
		for(Entry<String, Subscription> entry : btSubscriptionsMap.entrySet()) {
			
			String key = entry.getKey();
			if(checkedBTSubs.contains(key)) continue;
			
			warnings.add("Subscription not found on vital side, adding it now - id: ${key}")
			 
			Subscription subscription = entry.getValue();
			
			ServiceContract sc = synchronizeSubscription(null, null, subscription)
			
			String status = sc?.subscriptionStatus?.toString()
			
			if('ACTIVE' == status || 'PAST_DUE' == status || 'PENDING' == status) {
				if(activeContract != null) {
					warnings.add("More than 1 active contract found: ${sc.braintreeSubscriptionID} - ${status} ,  ${activeContract.braintreeSubscriptionID} - ${activeContract.subscriptionStatus}")
				}
				activeContract = sc
			}
			
		}
		
		String currentAccountPlanStatus = customer.accountPlanStatus?.toString()
		Integer currentDaysPastDue = customer.daysPastDue?.toString()
		
		String newAccountPlanStatus = null
		Integer newDaysPastDue = null
		
		if(activeContract != null) {

			String status = activeContract.subscriptionStatus.toString()
			
			if('ACTIVE' == status) {
				
				newAccountPlanStatus = 'valid'
				
			} else if('PAST_DUE' == status) {
			
				newDaysPastDue = activeContract.daysPastDue
			
				//if it's first cycle then we assume the pastdue occurred just after the trial period
				int currentCycle = activeContract.currentBillingCycle ? activeContract.currentBillingCycle.intValue() : 1
				
				if(currentCycle == 1 || newDaysPastDue.intValue() > DAYS_PAST_DUE_GRACE) {
					
					newAccountPlanStatus = 'invalid'
					
				} else {
				
					newAccountPlanStatus = 'grace-period'
					
				}
				
			}
						
		}
		
		if(currentAccountPlanStatus != newAccountPlanStatus || currentDaysPastDue?.intValue() != newDaysPastDue?.intValue() ) {
			
			customer.accountPlanStatus = newAccountPlanStatus
			customer.daysPastDue = newDaysPastDue
			
			ResultList rl = null
			if(!dryrun) {
				rl = scriptInterface.save(customersSegment, [customer])
			} else {
				rl = new ResultList()
			}
			
			if(rl.status.status != VitalStatus.Status.ok) {
				warnings.add("Error when updating customer status: ${rl.status.message}")
			}
			
			updatedInsertedObjects++;
			
			customerUpdated = true
			
		}
		
	}
	
	ServiceContract synchronizeSubscription(ServiceContract sc, Plan plan, Subscription btSubscription) {
		
		Map<String, GraphObject> toUpdate = [:]
		
		List<URIProperty> toDelete = []
		
		VITAL_Container container = new VITAL_Container()
		
		if(sc == null) {
			
			PaymentMethod pm = paymentMethodsMap.get(btSubscription.getPaymentMethodToken())
			if(pm == null) warnings.add("Payment method not found: ${btSubscription.getPaymentMethodToken()}")
			
			VitalSelectQuery sq = builder.query {
				SELECT {
					
					value segments: [productsSegment]
					
					value offset: 0
					
					value limit: 10
					
					node_constraint { Plan.class }
					
					node_constraint { ((Plan_PropertiesHelper)Plan.props()).braintreePlanID.equalTo(btSubscription.getPlanId() ) }
					
				}
			}.toQuery()
			
			
			ResultList planSelect = scriptInterface.query(sq)
			if(planSelect.status.status != VitalStatus.Status.ok) {
				warnings.add("Plan select error: ${planSelect.status.message}")
				return null
			}

			plan = (Plan) planSelect.first()
			
			if(plan == null) {
				warnings.add("plan not found: ${btSubscription.getPlanId()}")
				return null
			}			
			
			
			sc = BraintreeIntegrationScript.btSubscriptionToServiceContract(btSubscription)
			Edge_hasPlan planEdge = new Edge_hasPlan().addSource(sc).addDestination(plan).generateURI(app)
			Edge_hasSubscription subsEdge = new Edge_hasSubscription().addSource(customer).addDestination(sc).generateURI(app)
			
			
			container.putGraphObjects([sc, subsEdge, plan, planEdge])
			
			if(pm != null) {
				Edge_hasSelectedPaymentMethod pmEdge = new Edge_hasSelectedPaymentMethod().addSource(sc).addDestination(pm).generateURI(app)
						toUpdate.put(pmEdge.URI, pmEdge)
						container.putGraphObjects([pm, pmEdge])
			}
			
			//service contract
			toUpdate.put(sc.URI, sc)
			toUpdate.put(subsEdge.URI, subsEdge)
			toUpdate.put(planEdge.URI, planEdge)
			
			
		} else {
		
			//expand service contract now
		
			VitalGraphQuery vgq = builder.query {
				
				GRAPH {
					
					value segments: [customersSegment]
					
					value offset: 0
					
					value limit: 1000
					
					value inlineObjects: true
					
					ARC {
						
						node_constraint { "URI eq ${sc.URI}" }
						
						ARC {
							
							value direction: 'reverse'
							
							edge_constraint { Edge_hasServiceContract.class }
							
							node_constraint { InvoiceItem.class }
							
							ARC {
								
								value direction: 'reverse'
								
								edge_constraint { Edge_hasItem.class }
								
								node_constraint { Invoice.class }
							
								
								ARC {
									
									value optional: true
									
									edge_constraint { Edge_hasPaymentInfo.class }
									
									node_constraint { PaymentInfo.class }
								
									ARC {
										
										value optional: true

										edge_constraint { Edge_hasSelectedPaymentMethod.class }
										
									}
										
								}
									
							}
							
						}
						
					}
					
				}
				
			}.toQuery()
		
			ResultList rl = scriptInterface.query(vgq)
			
			if(rl.status.status != VitalStatus.Status.ok) {
				warnings.add("Error when querying for service contract details: ${sc.braintreeSubscriptionID}: ${rl.status.message}")
				return
			}
			
			rl = unpackGraphMatch(rl)
			
			for(GraphObject g : rl) {
				container.putGraphObject(g)
			}
			
			ServiceContract updatedContract = BraintreeIntegrationScript.btSubscriptionToServiceContract(btSubscription)
			updatedContract.URI = sc.URI
			
			if(!updatedContract.equals(sc)) {
				
				toUpdate.put(sc.URI, updatedContract)
				
				sc = updatedContract
				
			}
			
		}
			
		List<InvoiceItem> invoiceItems = sc.getServiceContractsReverse(ContainerCtx, container)
		
		Map<Integer, Invoice> billingCycle2Invoice = [:]
		
		Map<Integer, InvoiceItem> billingCycle2InvoiceItem = [:]
		
		for(InvoiceItem item : invoiceItems) {
			
			List<Invoice> invoices = item.getItemsReverse(ContainerCtx, container);
			
			Invoice invoice = invoices[0]
			
			Integer billingCycle = item.currentBillingCycle ? item.currentBillingCycle.intValue() : 0
			
			billingCycle2Invoice.put(billingCycle, invoice)
			
			billingCycle2InvoiceItem.put(billingCycle, item)
			
		}
		
		//sort transactions by start / end date
		Map<Long, List<Transaction>> startDate2Transactions = [:]
		
		for(Transaction t : btSubscription.getTransactions()) {

			Subscription s = t.getSubscription();
			if(s == null) continue;
			
			Long billingStartDateMillis = s.getBillingPeriodStartDate().getTimeInMillis()

			List<Transaction> transactions = startDate2Transactions.get(billingStartDateMillis)
			if(transactions == null) {
				transactions = []
				startDate2Transactions.put(billingStartDateMillis, transactions)
			}				
			
			transactions.add(t)
			
		}
		
		int currentBillingCycle = btSubscription.currentBillingCycle.intValue();
		
		if(startDate2Transactions.size() != btSubscription.currentBillingCycle.intValue()) {

			warnings.add("current billing cycle ${currentBillingCycle} is different than tx billing cycle start dates count: ${startDate2Transactions.size()}")
			
			return
							
		}
		
		List<Entry<Long, List<Transaction>>> periods = []
		for( Entry<Long, List<Transaction>> entry : startDate2Transactions.entrySet()) {
			periods.add(entry)
		}
		
		periods.sort { Entry<Long, List<Transaction>> e1 , Entry<Long, List<Transaction>> e2 ->
			return e1.getKey().compareTo(e2.getKey())
		}
		
		
		for( int billingCycle = 1 ; billingCycle <= periods.size(); billingCycle++ ) {
			
			Entry<Long, List<Transaction>> period = periods.get(billingCycle-1)
			
			List<Transaction> periodTransactions = period.getValue()
			
			Transaction topTransaction = periodTransactions[0]
			
			Date periodStart = topTransaction.getSubscription().getBillingPeriodStartDate().getTime();
			
			Date periodEnd = topTransaction.getSubscription().getBillingPeriodEndDate().getTime();
			
			//check if invoice exists
			Invoice invoice = billingCycle2Invoice.get(billingCycle)
			InvoiceItem item = billingCycle2InvoiceItem.get(billingCycle)
			
			Map<String, PaymentInfo> vitalTransactions = [:]
			
			if(invoice == null) {
				
				//create a new invoice object
				
				Date currentDate = new Date()
				
				String endCycle = null
				Integer numberOfCycles = plan.numberOfBillingCycles != null ? plan.numberOfBillingCycles.intValue() : null
				if(numberOfCycles != null && numberOfCycles > 0) {
					endCycle = "of ${plan.numberOfBillingCycles}"
				} else {
					endCycle = " [never expires]"
				}
				
				invoice = new Invoice()
				invoice.generateURI(app)
				invoice.name = "Invoice ${currentDate}, subscription of ${plan.braintreePlanID}, cycle: ${billingCycle} " + endCycle
				invoice.timestamp = currentDate.getTime()
				
				
				float subTotal = plan.price.floatValue()
				
				float discount = 0.0f
				
				float basePrice = subTotal - discount
				
				float tax = 0.0f
				
				float grandTotal = basePrice + tax
				
				grandTotal = ((float)Math.round(grandTotal * 100f)) / 100f
				
				
				invoice.tax = tax
				invoice.grandTotal = grandTotal
				invoice.subTotal = subTotal
				invoice.discount = discount
				
				item = new InvoiceItem()
				item.name = "Plan ${plan.braintreePlanID}, ${billingCycle} ${endCycle}, ${periodStart} - ${periodEnd}"
				item.generateURI(app)
				item.price = plan.price.floatValue()
				item.quantity = 1f
				item.totalPrice = plan.price.floatValue()
				item.billingPeriodEndDate = periodEnd
				item.billingPeriodStartDate = periodStart
				item.currentBillingCycle = currentBillingCycle
				Edge_hasInvoice invoiceEdge = new Edge_hasInvoice().addSource(customer).addDestination(invoice).generateURI(app)
				Edge_hasItem itemEdge = new Edge_hasItem().addSource(invoice).addDestination(item).generateURI(app)
				Edge_hasServiceContract serviceContractEdge = new Edge_hasServiceContract().addSource(item).addDestination(sc).generateURI(app)

				toUpdate.put(invoice.URI, invoice)
				toUpdate.put(item.URI, item)
				toUpdate.put(invoiceEdge.URI, invoiceEdge)
				toUpdate.put(itemEdge.URI, itemEdge)
				toUpdate.put(serviceContractEdge.URI, serviceContractEdge)
				
			} else {
			
				List<PaymentInfo> pis = invoice.getPaymentInfos(ContainerCtx, container);
				
				for(PaymentInfo pi : pis) {
					
					vitalTransactions.put(pi.braintreeTransactionID.toString(), pi)
					
				}
			
			}
			
			Set<String> coveredTransactions = new HashSet<String>()
			
			for(Transaction tx : periodTransactions) {
				
				PaymentInfo vt = vitalTransactions.get(tx.getId())

				if(vt == null) {
					
					vt = BraintreeIntegrationScript.btTransactionToPaymentInfo(tx)

					String token = vt.paymentMethod.toString()
					
					PaymentMethod method = paymentMethodsMap.get(token)
					if(method == null) {
						warnings.add("Payment method not found for token: ${token}")
					}
											
					Edge_hasPaymentInfo edge = new Edge_hasPaymentInfo().addSource(invoice).addDestination(vt).generateURI(app)
					toUpdate.put(vt.URI, vt)
					toUpdate.put(edge.URI, edge)
					
					if(method != null) {
						Edge_hasSelectedPaymentMethod pmEdge = new Edge_hasSelectedPaymentMethod().addSource(vt).addDestination(method).generateURI(app)
						toUpdate.put(pmEdge.URI, pmEdge)
					}
					
				} else {
				
					PaymentInfo updated = BraintreeIntegrationScript.btTransactionToPaymentInfo(tx)
					updated.URI = vt.URI
					
					coveredTransactions.add(vt.URI)
					
					if(!updated.equals(vt)) {
						toUpdate.put(updated.URI, updated)
					}
				
				}
				
			}
			
			for(PaymentInfo p : vitalTransactions.values()) {
				
				if(!coveredTransactions.contains(p.URI)) {
					
					toDelete.add(URIProperty.withString(p.URI))
					
					//delete non-existing transaction
					for(GraphObject g : p.getPaymentInfoEdgesIn(ContainerCtx, container)) {
						toDelete.add(URIProperty.withString(g.URI))
					}
					
					for(GraphObject g : p.getSelectedPaymentMethodEdges(ContainerCtx, container)) {
						toDelete.add(URIProperty.withString(g.URI))
						
					}
					
				}
				
			}
			
			
		}
			
			
		if(toUpdate.size() > 0) {
			
			ResultList saveRL = null;
			
			if(dryrun) {
				saveRL = new ResultList()
			} else {
				saveRL = scriptInterface.save(customersSegment, new ArrayList<GraphObject>(toUpdate.values()))
			}
			
			if(saveRL.status.status != VitalStatus.Status.ok) {
				
				warnings.add("Service contract ${sc.braintreeSubscriptionID} objects saving error: ${saveRL.status.message}")
			
			} else {
			
				updatedInsertedObjects += toUpdate.size()
			
			}
			
			
			
		}
		
		if(toDelete.size() > 0) {
			
			VitalStatus deleteRL = null
			
			if(dryrun) {
				
				deleteRL = VitalStatus.withOK()
				
			} else {
			
				deleteRL = scriptInterface.delete(toDelete)
			
			}
			
			if(deleteRL.status != VitalStatus.Status.ok) {
				
				warnings.add("Service contract ${sc.braintreeSubscriptionID} objects delete error: ${deleteRL.status.message}")
				
			} else {
			
				deletedObjects += toDelete.size()
			
			}
			

		}
		
		
		return sc
		
		
	}
	
	
	
	private List<VitalSegment> segmentsList(List<VitalSegment> segments) {
		
		Set<String> ids = new HashSet<String>()
		
		List<VitalSegment> out = []
		
		for(VitalSegment s : segments) {
			
			if(ids.add(s.segmentID.toString())) {
				out.add(s)
			}
			
		}
		
		return out
		
	}
	
	public static ResultList unpackGraphMatch(ResultList rl) {
		
		ResultList r = new ResultList();
				
		for(GraphObject g : rl) {
					
			if(g instanceof GraphMatch) {
						
				for(Entry<String, IProperty> p : g.getPropertiesMap().entrySet()) {
							
							
					IProperty unwrapped = p.getValue().unwrapped();
					if(unwrapped instanceof StringProperty) {
						GraphObject x = CompactStringSerializer.fromString((String) unwrapped.rawValue());
						if(x != null) r.getResults().add(new ResultElement(x, 1D));
					}
							
				}
					
			} else {
				throw new RuntimeException("Expected graph match objects only");
			}
					
		}
			
		return r;
	}
}
