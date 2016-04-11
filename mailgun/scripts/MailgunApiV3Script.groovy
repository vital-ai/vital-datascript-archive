package commons.scripts

import java.util.Map
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.util.Map.Entry

import org.apache.commons.codec.binary.Base64
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.HttpConnectionManager
import org.apache.commons.httpclient.HttpMethodBase
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager
import org.apache.commons.httpclient.UsernamePasswordCredentials
import org.apache.commons.httpclient.auth.AuthScope
import org.apache.commons.httpclient.methods.DeleteMethod
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource
import org.apache.commons.httpclient.methods.multipart.FilePart
import org.apache.commons.httpclient.methods.multipart.FilePartSource
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity
import org.apache.commons.httpclient.methods.multipart.Part
import org.apache.commons.httpclient.methods.multipart.StringPart
import org.codehaus.jackson.map.ObjectMapper

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptHooks
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.vitalservice.VitalStatus
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalsigns.model.VITAL_GraphContainerObject
import ai.vital.vitalsigns.model.VitalApp

import com.vitalai.domain.genericapp.MailingListMember

/**
 * Mailgun API v3 script
 * api key and domain are params now
 * there's a shared connection pool manager disposed when the script is unloaded
 * @author Derek
 *
 */
class MailgunApiV3Script implements VitalPrimeGroovyScript, VitalPrimeScriptHooks {

	/** CONFIG **/
	
	static int MAX_CONNETIONS_COUNT = 5
	
	/* END OF CONFIG */
	
	static MultiThreadedHttpConnectionManager httpConnectionManager = null
	
	
	static MultiThreadedHttpConnectionManager initConnectionManager() {

		if(httpConnectionManager == null) {
			synchronized (MailgunApiV3Script.class) {
				if(httpConnectionManager == null) {
					httpConnectionManager = new MultiThreadedHttpConnectionManager();
					httpConnectionManager.setMaxConnectionsPerHost(5)
					httpConnectionManager.setMaxTotalConnections(5)
				}
			}
		}
		
		return httpConnectionManager;

	}
	
	@Override
	public void onUnload() {

		if(httpConnectionManager != null) {
			try {
				httpConnectionManager.shutdown();
			} catch(Exception e) {}
			httpConnectionManager = null
			
		}
		

	}

	@Override
	public void onLoad() {
		//do nothing
	}


	@Override
	public ResultList executeScript(
			VitalPrimeScriptInterface scriptInterface,
			Map<String, Object> params) {

		ResultList rl = new ResultList()

		try {

			String action = params.get('action')
			if(!action) throw new Exception("No 'action' param")
			
			String apiKey = params.get('apiKey')
			if(!apiKey) throw new Exception("No 'apiKey' param")

			String 	domain = params.get('domain')
			if(!domain) throw new Exception("No 'domain' param")
			
			MailgunV3Client client = new MailgunV3Client(domain, apiKey, initConnectionManager())			
			
			if(action == 'sendEmail') {
				
				sendEmail(params, client, rl)
				
			} else if(action == 'addMembers') {
			
				addMembers(params, client, rl)
				
			} else if(action == 'getMailingList') {
			
				getMailingList(params, client, rl)
				
			} else if(action == 'getMailingListMembers') {
			
				getMailingListMembers(params, client, rl)
			
			} else if(action == 'deleteMember') {
			
				deleteMember(params, client, rl)
			
			} else if(action == 'getDomains') {
			
				getDomains(params, client, rl)
					
			} else {
				throw new Exception('Unknown action')
			}
			

		} catch(Exception e) {
			rl.status = VitalStatus.withError(e.localizedMessage)
		}

		return rl;
	}
			
	void getDomains(Map<String, Object> params, MailgunV3Client client, ResultList rl) throws Exception {
	
		Map<String, Object> res = client.getDomains();

		List items = res.items
		
		rl.totalResults = res.get('total_count')
		
		for(Map<String, Object> o : items) {
			
			VITAL_GraphContainerObject gco = new VITAL_GraphContainerObject()
			gco.generateURI((VitalApp) null)
			
			gco.created_at = o.get('created_at')
			gco.name = o.get('name')
			gco.state = o.get('state')
			
			rl.addResult(gco)
			
		}
//		
//		
//		{
//			"created_at": "Wed, 10 Jul 2013 19:26:52 GMT",
//			"smtp_login": "postmaster@samples.mailgun.org",
//			"name": "samples.mailgun.org",
//			"smtp_password": "4rtqo4p6rrx9",
//			"wildcard": true,
//			"spam_action": "disabled",
//			"state": "active"
//		  }
		
				
//		{
//			"created_at": "Wed, 10 Jul 2013 19:26:52 GMT",
//			"smtp_login": "postmaster@samples.mailgun.org",
//			"name": "samples.mailgun.org",
//			"smtp_password": "4rtqo4p6rrx9",
//			"wildcard": true,
//			"spam_action": "disabled",
//			"state": "active"
//		  }
		
		
			
	}
	
	
	void deleteMember(Map<String, Object> params, MailgunV3Client client, ResultList rl) throws Exception {

		String listName = params.get('listName')
		if(!listName) throw new Exception("No 'listName' param")

		String email = params.get('email')
		if(!email) throw new Exception("No 'email' param")
		
		Map<String, Object> res = client.deleteMember(listName, email)
		
		String m = res.get("message")
		if(m == null) m = '(no response message)'
		
		rl.status = VitalStatus.withOKMessage(m)
				
	}
			
	void getMailingListMembers(Map<String, Object> params, MailgunV3Client client, ResultList rl) throws Exception {
		
		String listName = params.get('listName')
		if(!listName) throw new Exception("No 'listName' param")
		
		Boolean subscribed = params.get('subscribed')
		Integer limit = params.get('limit')
		Integer skip = params.get('skip')
		
		List<MailingListMember> mlms = client.getMailingListMembers(listName, subscribed, limit, skip)
		
		for(MailingListMember mlm : mlms) {
			rl.addResult(mlm)
		}
		
	}
			
	void getMailingList(Map<String, Object> params, MailgunV3Client client, ResultList rl) throws Exception {

		String listName = params.get('listName')
		if(!listName) throw new Exception("No 'listName' param")
		
		Map ml = client.getMailingList(listName)
		
		//convert that into graph container object
		VITAL_GraphContainerObject c = new VITAL_GraphContainerObject()
		c.generateURI((VitalApp) null)
		c.mailingListJson = JsonOutput.toJson(ml)
		rl.addResult(c)
		
	}
			
	void addMembers(Map<String, Object> params, MailgunV3Client client, ResultList rl) throws Exception {
		
		String listName = params.get('listName')
		if(!listName) throw new Exception("No 'listName' param")
		
		List<MailingListMember> members = (List<MailingListMember>) params.get('members')
		if(members == null || members.size() == 0) throw new Exception("No 'members' or empty list param")
		
		Boolean upsert = params.get('upsert')
		if(upsert == null) throw new Exception("No 'upsert' param")
		
//		public Map<String, Object> addMembers(String listName, List<MailingListMember> members, boolean upsert) {
		
		Map<String, Object> res = client.addMembers(listName, members, upsert)
		
		String m = res.get("message")
		if(m == null) m = '(no response message)'
		
		rl.status = VitalStatus.withOKMessage(m)
		
	}
			
	void sendEmail(Map<String, Object> params, MailgunV3Client client, ResultList rl) throws Exception {
		
		String from = params.get('from')
		if(!from) throw new Exception("No 'from' param")
		String to = params.get('to')
		if(!to) throw new Exception("No 'to' param")
		String cc = params.get('cc')
		String subject = params.get('subject')
		if(!subject) throw new Exception("No 'subject' param")
		String text = params.get('text')
		if(!text) throw new Exception("No 'text' param")
		String html = params.get('html')
		if(!html) throw new Exception("No 'html' param")


		Map<String, byte[]> inlineByteAttachements = null

		Map inlineByteAttachementsMap = params.get('inlineByteAttachements')

		//base 64 encoding
		if(inlineByteAttachementsMap != null) {

			inlineByteAttachements = [:]

			for(Entry<String, String> e : inlineByteAttachementsMap.entrySet()) {

				//convert base64 to byte[]
				byte[] decoded = Base64.decodeBase64(e.getValue())
				inlineByteAttachements.put(e.getKey(), decoded)

			}
		}

		//don't use them
		Map<String, File> inlineFileAttachments = null

		SendMessageResponse resp = client.sendMessage(from, to, cc, subject, text, html, inlineByteAttachements, inlineFileAttachments)

		rl.status = VitalStatus.withOKMessage("Email ID: ${resp.id}, message: ${resp.message}")
		
	}

	static class MailgunV3Client {

		private HttpClient httpClient;

		public final static String apiURL = "https://api.mailgun.net/v3";

		private static ObjectMapper mapper = new ObjectMapper();

		private String key;

		private String domain;

		private String urlBase;

		public MailgunV3Client(String domain, String key, HttpConnectionManager connectionManager) {
			this.domain = domain;
			this.key = key;
			httpClient = new HttpClient(connectionManager)
			httpClient.getState().setAuthenticationPreemptive(true)
			httpClient.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("api", key))

			urlBase = apiURL + '/' + domain + '/';
		}


		public SendMessageResponse sendMessage(String from, String to, String cc, String subject, String text, String html) throws IOException {

			PostMethod postMethod = new PostMethod(urlBase + "messages");


			//postMethod.setContentChunked(true);

			/*
			 Part[] parts = new Part[]{
			 new StringPart("from", from, "UTF-8"),
			 new StringPart("to", to, "UTF-8"),
			 new StringPart("cc", cc, "UTF-8"),
			 new StringPart("subject", subject, "UTF-8"),
			 new StringPart("text", text, "UTF-8"),
			 new StringPart("html", html, "UTF-8")
			 };
			 */

			//		MultipartRequestEntity requestEntity = new MultipartRequestEntity(parts, postMethod.getParams());


			String body = "from=" + e(from) + "&to=" + e(to) + "&cc=" + e(cc) + "&subject=" + e(subject)
			+ "&text=" + e(text) + "&html=" + e(html);

			/*
			 postMethod.setRequestBody(new NameValuePair[]{
			 new NameValuePair("from", from),
			 new NameValuePair("to", to),
			 new NameValuePair("cc", cc),
			 new NameValuePair("subject", subject),
			 new NameValuePair("text", text),
			 new NameValuePair("html", html)
			 });
			 */
			//		long length = postMethod.getRequestEntity().getContentLength();

			long length = body.getBytes("UTF-8").length;


			//		String length = "" + requestEntity.getContentLength();
			postMethod.addRequestHeader("Content-Type", "application/x-www-form-urlencoded");
			postMethod.addRequestHeader("Content-Length", "" + length);
			postMethod.setRequestBody(body);
			//		postMethod.setRequestEntity(requestEntity);



			try {

				int status = httpClient.executeMethod(postMethod);

				String responseBody = null;

				try {
					responseBody = postMethod.getResponseBodyAsString();
				} catch(Exception e) {}

				if(status != 200) {
					throw new IOException("HTTP Error - " + status + " - " +responseBody);
				}

				//process body

				return mapper.readValue(responseBody, SendMessageResponse.class);


			} finally {

				postMethod.releaseConnection();

			}

		}

		private String e(String s) {
			try {
				return URLEncoder.encode(s, "UTF-8");
			} catch (UnsupportedEncodingException e1) {
			}
			return "";
		}



		public SendMessageResponse sendMessage(String from, String to, String cc, String subject, String text, String html,
				Map<String, byte[]> inlineByteAttachments, Map<String, File> inlineFileAttachments) throws IOException {

			PostMethod postMethod = new PostMethod(urlBase + "messages");

			Part[] parts = null;//new Part[6 + attachments.size()];

			List<Part> list = new ArrayList<Part>();

			if(from != null && !from.isEmpty()) {
				list.add(new StringPart("from", from, "UTF-8"));
			}

			if(to != null && !to.isEmpty()) {
				list.add(new StringPart("to", to, "UTF-8"));
			}

			if(cc != null && !cc.isEmpty()) {
				list.add(new StringPart("cc", cc, "UTF-8"));
			}

			if(subject != null && !subject.isEmpty()) {
				list.add(new StringPart("subject", subject, "UTF-8"));
			}

			if(text != null && !text.isEmpty()) {
				list.add(new StringPart("text", text, "UTF-8"));
			}

			if(html != null && !html.isEmpty()) {
				list.add(new StringPart("html", html, "UTF-8"));
			}


			if(inlineByteAttachments != null) {

				for( Iterator<Entry<String, byte[]>> iterator = inlineByteAttachments.entrySet().iterator(); iterator.hasNext(); ) {

					Entry<String, byte[]> next = iterator.next();

					String fn = next.getKey();

					byte[] bytes = next.getValue();

					list.add(new FilePart("inline", new ByteArrayPartSource(fn, bytes)));
				}

			}


			if(inlineFileAttachments != null) {

				for( Iterator<Entry<String, File>> iterator = inlineFileAttachments.entrySet().iterator(); iterator.hasNext(); ) {

					Entry<String, File> next = iterator.next();

					list.add(new FilePart("inline", new FilePartSource(next.getKey(), next.getValue())));

				}

			}

			MultipartRequestEntity requestEntity = new MultipartRequestEntity(list.toArray(new Part[list.size()]), postMethod.getParams());

			//		long length = requestEntity.getContentLength();

			//		String length = "" + requestEntity.getContentLength();
			//postMethod.addRequestHeader("Content-Type", "multipart/form-data");
			//		postMethod.addRequestHeader("Content-Length", "" + length);
			postMethod.setRequestEntity(requestEntity);


			try {

				int status = httpClient.executeMethod(postMethod);

				String responseBody = null;

				try {
					responseBody = postMethod.getResponseBodyAsString();
				} catch(Exception e) {}

				if(status != 200) {
					throw new IOException("HTTP Error - " + status + " - " +responseBody);
				}

				//process body

				return mapper.readValue(responseBody, SendMessageResponse.class);


			} finally {

				postMethod.releaseConnection();

			}

		}

		public Map<String, Object> getMailingList(String listName) throws IOException {
			
			GetMethod getMethod = new GetMethod(apiURL + '/lists/' + listName + '@' + domain )
			
			return (Map<String, Object>) handleJsonResponse(getMethod)
			
		}
		
		public List<MailingListMember> getMailingListMembers(String listName, Boolean subscribed, Integer limit, Integer skip) {
			
			List<MailingListMember> l = new ArrayList<MailingListMember>() 
			
			String url = apiURL + '/lists/' + listName + '@' + domain + '/members'
			
			Map<String, String> paramsMap = new LinkedHashMap<String, String>()
			if(subscribed != null) {
				paramsMap.put('subscribed', subscribed.booleanValue() ? 'yes' : 'no')
			}
			if(limit != null) {
				paramsMap.put('limit', '' + limit)
			}
			if(skip != null) {
				paramsMap.put('skip', '' + skip)
			}
			
			if(paramsMap.size() > 0) {
				url += '?'
				boolean first = true
				for(Entry<String, String> e : paramsMap.entrySet()) {
					if(first) {
						first = false
					} else {
						url += '&'
					}
					
					url += ( e.getKey() + '=' + e.getValue())
				}
			}
			
			
			GetMethod getMethod = new GetMethod(url)
			
			Map<String, Object> res = handleJsonResponse(getMethod)
			
			List items = res.get('items')
			
			for(Map item : items) {
				
				MailingListMember mlm = new MailingListMember()
				mlm.generateURI((VitalApp) null)
				mlm.name = item.get('name')
				mlm.emailActive = item.get('subscribed')
				mlm.email = item.get('address')

				Map vars = item.get('vars')
				
				if(vars != null) {
					
					Number dateSubscribed = vars.get('dateSubscribed')
					
					if(dateSubscribed != null) {
						mlm.dateSubscribed = new Date(dateSubscribed.longValue())
					}
					
					mlm.organization = vars.get('organization')

					mlm.randomCode = vars.get('randomCode')
										
				}
			
				l.add(mlm)
									
			}
			
//			{
//				"items": [
//					{
//						"vars": {
//							"age": 26
//						},
//						"name": "Foo Bar",
//						"subscribed": false,
//						"address": "bar@example.com"
//					}
//				],
//				"total_count": 1
//			  }
			
			
//			GET /lists/<address>/members

			return l
						
		}
		
		//may be used to update emails too
		public Map<String, Object> addMembers(String listName, List<MailingListMember> members, boolean upsert) {
			
			
			PostMethod postMethod = new PostMethod(apiURL + '/lists/' + listName + '@' + domain + '/members.json')

			postMethod.addParameter('upsert', upsert ? 'yes' : 'no')
			
			//to json
			
			List jsonList = []
			
			for(MailingListMember mlm : members) {
				
				Boolean emailActive = mlm.emailActive
				String email = mlm.email
				String name = mlm.name
				if(emailActive == null) emailActive = false
				
				String organization = mlm.organization
				Map vars = [:]
				if(organization) {
					vars.organization = organization
				}
				
				//timestamp
				Date dateSubscribed = mlm.dateSubscribed
				if(dateSubscribed != null) {
					vars.dateSubscribed = dateSubscribed.getTime()
				}
				
				String randomCode = mlm.randomCode
				if(randomCode != null) {
					vars.randomCode = randomCode
				}
				
				Map record = ["address": email, "name": name, "subscribed": emailActive, vars: vars]
				
				jsonList.add(record)
				
			}
			
			String membersJsonString = null;
			
			postMethod.addParameter('members', JsonOutput.toJson(jsonList))
			
			return (Map<String, Object>) handleJsonResponse(postMethod)
			
		}
		
		public Map<String, Object> deleteMember(String listName, String email) {
			
			DeleteMethod deleteMethod = new DeleteMethod(apiURL + '/lists/' + listName + '@' + domain + '/members/' + email)
			
			return handleJsonResponse(deleteMethod)
			
		}
		
		public Map<String, Object> getDomains() {
			
			GetMethod getMethod = new GetMethod(apiURL + '/domains')
			
			return handleJsonResponse(getMethod)
			
		}
		
		protected Object handleJsonResponse(HttpMethodBase method) throws IOException {
			
			try {
				
				int status = httpClient.executeMethod(method);
				
				String responseBody = null;
				
				try {
					responseBody = method.getResponseBodyAsString();
				} catch(Exception e) {}
				
				if(status != 200) {
					throw new IOException("HTTP Error - " + status + " - " +responseBody);
				}
				
				//process body
				
				return new JsonSlurper().parseText(responseBody)
				
			} finally {
				method.releaseConnection()
			}
			
		}
		//handle json response

	}


	static class SendMessageResponse {

		private String id;

		private String message;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

	}

}
