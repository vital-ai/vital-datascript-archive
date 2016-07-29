package commons.scripts

import java.util.List;
import java.util.Map

import com.amazon.ecs.client.AWSECommerceService
import com.amazon.ecs.client.AWSECommerceServicePortType
import com.amazon.ecs.client.EditorialReview;
import com.amazon.ecs.client.EditorialReviews;
import com.amazon.ecs.client.Item
import com.amazon.ecs.client.ItemLookup
import com.amazon.ecs.client.ItemLookupRequest
import com.amazon.ecs.client.ItemLookupResponse
import com.amazon.ecs.client.ItemSearchRequest
import com.amazon.ecs.client.ItemSearchResponse
import com.amazon.ecs.client.ItemSearch
import com.amazon.ecs.client.Items
import com.amazon.ecs.client.Price
import com.amazon.ecs.client.utils.AwsHandlerResolver

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.VitalSigns
import ai.vital.vitalsigns.model.VITAL_Node

class AmazonProductsSearchScript implements VitalPrimeGroovyScript {

	static AWSECommerceService service = null

	static AWSECommerceServicePortType port
	
	static String awsEcsAccessKey = null
	
	static String awsEcsAssociateTag = null	
	
	static void initService(VitalPrimeScriptInterface scriptInterface) {
	
		if(service == null) {
			
			synchronized (AmazonProductsSearchScript.class) {
				
				if(service == null) {
				
					String orgID = scriptInterface.getOrganization().organizationID
					
					String appID = scriptInterface.getApp().appID
					
					Map orgConf = VitalSigns.get().getConfig(orgID)
					
					if(!orgConf) throw new Exception("No organization config: ${orgID}")
					
					Map appConf = orgConf.get(appID)
					
					if(!appConf) throw new Exception("No app '${appID}' config for organization: ${orgID}")
					
					awsEcsAccessKey = appConf.get("awsEcsAccessKey")
					String awsEcsSecretKey = appConf.get("awsEcsSecretKey")
					awsEcsAssociateTag = appConf.get("awsEcsAssociateTag")
					
					if(!awsEcsAccessKey) throw new Exception("No awsEcsAccessKey config param")
					if(!awsEcsSecretKey) throw new Exception("No awsEcsSecretKey config param")
					if(!awsEcsAssociateTag) throw new Exception("No awsEcsAssociateTag config param")

					// Set the service:
					service = new AWSECommerceService();
					
					service.setHandlerResolver(new AwsHandlerResolver(awsEcsSecretKey, false));
					
					//Set the service port:
					port = service.getAWSECommerceServicePortUS();
											
				}
				
			}
			
		}
		
	}
	
	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> parameters) {

	
		ResultList rl = new ResultList()
		
		try {
			
			initService(scriptInterface)
			
			String searchIndex = 'All'
			
			Integer limit = 10
			
			Integer limitParam = parameters.get('limit')
			if(limitParam != null) {
				limit = limitParam
				if(limit <=0 ) throw new Exception("limit param must be > 0")
			}
			
			String searchIndexParam = parameters.get('searchIndex')
			if(searchIndexParam) {
				searchIndex = searchIndexParam
			}
			
			String keywords = parameters.get('keywords')
			if(!keywords) throw new Exception("No 'keywords' param")
			
			
			//Get the operation object:
			ItemSearchRequest itemSearchRequest = new ItemSearchRequest();
			//Fill in the request object:
			itemSearchRequest.setSearchIndex(searchIndex);
			itemSearchRequest.setKeywords(keywords);
	//		itemRequest.setVersion("2011-08-01");
			ItemSearch itemSearch= new ItemSearch();
			itemSearch.setAssociateTag(awsEcsAssociateTag);
			itemSearch.setAWSAccessKeyId(awsEcsAccessKey);
			itemSearchRequest.getResponseGroup().add("Medium")
			itemSearch.getRequest().add(itemSearchRequest);
			//Call the Web service operation and store the response
			//in the response object:
			
			ItemSearchResponse response = port.itemSearch(itemSearch);
			
			List<Items> items = response.getItems();
	
			
			List<String> asins = []
			
			for(Items item : items) {
				
				List<Item> item2 = item.getItem();
				
				for(Item i : item2 ) {
					
					asins.add(i.getASIN())

					VITAL_Node node = new VITAL_Node().generateURI(scriptInterface.getApp())

					node."urn:ASIN" = i.getASIN()

					node."urn:smallImageURL" = i.getSmallImage()?.getURL()
					node."urn:mediumImageURL" = i.getMediumImage()?.getURL()
					node."urn:largeImageURL" = i.getLargeImage()?.getURL()

					node."urn:link" = i.getDetailPageURL()
					
					node.name = i.getItemAttributes().getTitle()

					Price price = i.getOfferSummary()?.getLowestNewPrice()
					
					if(price != null) {
						
						node."urn:priceFormatted" = price.getFormattedPrice()
						node."urn:priceAmount" = price.getAmount().intValue()
						node."urn:priceCurrencyCode" = price.getCurrencyCode()
						
					}

					//description					
					EditorialReviews reviews = i.getEditorialReviews()
					if(reviews != null) {
						List<EditorialReview> ers = reviews.getEditorialReview()
						EditorialReview review = null
						if(ers != null) {
							for(EditorialReview r : ers) {
								if(r.getSource() == 'Product Description') {
									node."urn:description" = r.getContent()
								}
							}	
						} 
					}
					
					rl.addResult(node)
					
					if(rl.results.size() >= limit) break
										
				}
				
				
			}
			
		} catch(Exception e) {
			rl.status = VitalStatus.withError(e.localizedMessage)
		}
		
		return rl
		
	}

}
