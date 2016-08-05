package commons.scripts

import java.util.List;
import java.util.Map
import java.util.regex.Matcher
import java.util.regex.Pattern

import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.slf4j.Logger
import org.slf4j.LoggerFactory;

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

import ai.haley.shopping.domain.AmazonItem;
import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.VitalSigns
import ai.vital.vitalsigns.model.VITAL_Node
import ai.vital.vitalsigns.model.VitalApp
import commons.scripts.AmazonProductsSearchScript.ReviewsRating;

class AmazonProductsSearchScript implements VitalPrimeGroovyScript {

	static boolean useIframeMethod = true 
	
	private final static Logger log = LoggerFactory.getLogger(AmazonProductsSearchScript.class)
	
	static AWSECommerceService service = null

	static AWSECommerceServicePortType port
	
	static String awsEcsAccessKey = null
	
	static String awsEcsAssociateTag = null	
	
	static HttpClient httpClient = null
	
	static void initHttpClient() {
		
		if(httpClient == null) {
			synchronized(AmazonProductsSearchScript.class) {

				if(httpClient == null) {
					
					httpClient = new HttpClient()
					
				}
			}
		}
		
	}
	
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

			initHttpClient()
						
			initService(scriptInterface)
			
			String searchIndex = 'All'
			
			Integer limit = 10
			
			Integer limitParam = parameters.get('limit')
			if(limitParam != null) {
				limit = limitParam
				if(limit <=0 ) throw new Exception("limit param must be > 0")
			}
			
			
			Boolean includeRating = parameters.get('includeRating')
			if(includeRating == null) includeRating = false
			
			String searchIndexParam = parameters.get('searchIndex')
			if(searchIndexParam) {
				searchIndex = searchIndexParam
			}
			
			Long ratingDelay = parameters.get('ratingDelay')
			
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
			if(includeRating.booleanValue()) {
				itemSearchRequest.getResponseGroup().add("Reviews");
			}
			itemSearch.getRequest().add(itemSearchRequest);
			//Call the Web service operation and store the response
			//in the response object:
			
			ItemSearchResponse response = port.itemSearch(itemSearch);
			
			List<Items> items = response.getItems();
	
			
			List<String> asins = []
			
			StringBuilder errorMessages = new StringBuilder()
			int errors = 0
			
			for(Items item : items) {
				
				List<Item> item2 = item.getItem();
				
				for(Item i : item2 ) {
					
					asins.add(i.getASIN())

					AmazonItem node = new AmazonItem().generateURI((VitalApp) null);

					node.asin = i.getASIN()
					
					node.smallImageURL = i.getSmallImage()?.getURL()
					node.mediumImageURL = i.getMediumImage()?.getURL()
					node.largeImageURL = i.getLargeImage()?.getURL()

					node.url = i.getDetailPageURL()
					
					node.name = i.getItemAttributes().getTitle()

					Price price = i.getOfferSummary()?.getLowestNewPrice()

					//http://docs.aws.amazon.com/AWSECommerceService/latest/DG/minimum-advertised-price.html					
					if(price == null || price.getAmount() == null) {
						continue
					}
					
					node.priceFormatted = price.getFormattedPrice()
					node.priceAmount = price.getAmount().intValue()
					node.priceCurrencyCode = price.getCurrencyCode()

					//description					
					EditorialReviews reviews = i.getEditorialReviews()
					if(reviews != null) {
						List<EditorialReview> ers = reviews.getEditorialReview()
						EditorialReview review = null
						if(ers != null) {
							for(EditorialReview r : ers) {
								if(r.getSource() == 'Product Description') {
									node.description = descriptionFilter( r.getContent() )
								}
							}	
						} 
					}
					
					if(includeRating.booleanValue()) {
						
						
						String iframeURL = null
						String iframeHTML = null
						
						GetMethod getMethod = null
						try {
							
							ReviewsRating rr = null
							
							if(ratingDelay != null && rl.results.size() > 0) {
								Thread.sleep(ratingDelay.longValue())
							}
							
							if(i.getCustomerReviews().isHasReviews().booleanValue()) {
								
								if(useIframeMethod) {
									
									iframeURL = i.getCustomerReviews().getIFrameURL()
									
								} else {
								
									//fallback to reviews page scraping
									String pageURL = 'https://www.amazon.com/product-reviews/' + i.getASIN()
									
									iframeURL = pageURL
									
								}
								
								
								
								getMethod = new GetMethod(iframeURL)
								getMethod.setRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36")
								
								
								int status = httpClient.executeMethod(getMethod)
								
								try {
									iframeHTML = getMethod.getResponseBodyAsString()
								} catch(Exception e) {}
								
								if(status < 200 || status >= 299) {
									throw new Exception("Http error: ${status} + ${iframeHTML}")
								}
								
								if(useIframeMethod) {
									
									rr = parseReviewsIframeHTML(iframeHTML)
									
								} else {
								
									rr = parseReviewsPageHTML(iframeHTML);
									
								}
								
							} else {
							
								rr = new ReviewsRating(totalReviews: 0)
							
							}
							
							node.averageRating = rr.averageRating
							node.ratingsCount = rr.totalReviews
							
						} catch(Exception e) {
							log.error(e.localizedMessage + "\n" + iframeHTML)
							errors ++
							if(errorMessages.length() > 0) errorMessages.append("\n")
							if(iframeURL) errorMessages.append(iframeURL + ' ')
							errorMessages.append(e.localizedMessage)
						} finally {
						
							if(getMethod != null) getMethod.releaseConnection()
						
						}
						
						
					}
					
					rl.addResult(node)
					
					if(rl.results.size() >= limit) break
										
				}
				
				
			}
			
			rl.status.errors = errors
			rl.status.message = errorMessages.toString()
		} catch(Exception e) {
			rl.status = VitalStatus.withError(e.localizedMessage)
		}
		
		return rl
		
	}
	
	public static class ReviewsRating {
		
		//0.0 - 5.0
		Double averageRating
		
//		double maxRating
		
		Integer totalReviews
		
//		double _5starPercent
//		double _4starPercent
//		double _3starPercent
//		double _2starPercent
//		double _1starPercent
		
	}
	
	static Pattern altPattern = Pattern.compile('((\\d+)?(\\.)?(\\d+)?) out of 5 stars')
	
	static Pattern aTextPattern = Pattern.compile('(\\d+) customer reviews?')
	
	public static ReviewsRating parseReviewsIframeHTML(String htmlContent) throws Exception {
		
		Document doc = Jsoup.parse(htmlContent)
		
		Elements avgStars = doc.select('span.crAvgStars')
		
		if(avgStars.size() != 1) throw new Exception("Expected exactly 1 span.crAvgStars, got: ${avgStars.size()}")
		
		Element avgStarsEl = avgStars.first()
		
		Elements avgImgs = avgStarsEl.select('img')
		
		if(avgImgs.size() != 1) throw new Exception("Expected exactly 1 'span.crAvgStars img' element, got: ${avgImgs.size()}")

		String alt = avgImgs.first().attr('alt')
		
		Matcher m = altPattern.matcher(alt)
		if(!m.matches()) throw new Exception("span.crAvgStars img[alt] does not match pattern: ${altPattern.pattern()}: ${alt}")		 
		
		double avg = Double.parseDouble( m.group(1) )
		
		int totalReviews = 0
		
		Elements directLinks = avgStars.select(":root > a")
		
		if(directLinks.size() != 1) throw new Exception("Expected exactly one 'span.crAvgStars > a' element, got: ${directLinks.size()}")
		
		String aText = directLinks.first().text()
		
		Matcher am = aTextPattern.matcher(aText)
		
		if(am.matches()) {
			
			totalReviews = Integer.parseInt(am.group(1))
			
		}
		
		return new ReviewsRating(averageRating: avg, totalReviews: totalReviews)
		
		
		
	}
	
	static Pattern ratingPattern = Pattern.compile('((\\d+)?(\\.)?(\\d+)?) out of 5 stars')
	
	public static ReviewsRating parseReviewsPageHTML(String htmlContent) {
		
		Document doc = Jsoup.parse(htmlContent)
		
		Elements  reviewNumericalSummary = doc.select('div.reviewNumericalSummary')
		
		if( reviewNumericalSummary.size() != 1) throw new Exception("Expected exactly 1 div.reviewNumericalSummary, got: ${ reviewNumericalSummary.size()}")
		
		Element reviewNumericalSummaryEl =  reviewNumericalSummary.first()
		
		Elements arpRating = reviewNumericalSummaryEl.select('.arp-rating-out-of-text')
		
		if(arpRating.size() != 1) throw new Exception("Expected exactly 1 'div.reviewNumericalSummary .arp-rating-out-of-textimg' element, got: ${arpRating.size()}")

		String arpText = arpRating.first().text()
		
		Matcher m = ratingPattern.matcher(arpText)
		if(!m.matches()) throw new Exception("'div.reviewNumericalSummary .arp-rating-out-of-textimg' text does not match pattern: ${ratingPattern.pattern()}: ${arpText}")
		
		double avg = Double.parseDouble( m.group(1) )
		
		int totalReviews = 0
		
		Elements totalReviewsCount = reviewNumericalSummaryEl.select("span.totalReviewCount")
		
		if(totalReviewsCount.size() != 1) throw new Exception("Expected exactly one 'div.reviewNumericalSummary span.totalReviewCount' element, got: ${totalReviewsCount.size()}")
		
		totalReviews = Integer.parseInt(totalReviewsCount.first().text());
		
		
		return new ReviewsRating(averageRating: avg, totalReviews: totalReviews)
		
	}

	static String descriptionFilter( String s ) {
		
		s = s.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
		if(s.startsWith("Product Description ")) s=s.substring("Product Description ".length()).trim()
		if(s.length() > 200) {
			s = s.substring(0, 198) + '...'
		}
		
		
		return s
		
	}
}
