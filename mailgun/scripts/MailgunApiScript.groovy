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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.FilePartSource;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
//import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.map.ObjectMapper;

import ai.vital.prime.groovy.VitalPrimeGroovyScript;
import ai.vital.prime.groovy.VitalPrimeScriptInterface;
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList;

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

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.FilePartSource;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
//import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.map.ObjectMapper;

class MailgunApiScript implements VitalPrimeGroovyScript {

	static String API_KEY = '<KEY>'
	static String DOMAIN = '<DOMAIN>'
	
	static MailgunClient client = null
	
	static MailgunClient initClient() {
		if(client == null) {
			synchronized (MailgunApiScript.class) {
				if(client == null) {
					client = new MailgunClient(DOMAIN, API_KEY, true)
				}
			}
		}
		
		return client
	}
	
	@Override
	public ResultList executeScript(
			VitalPrimeScriptInterface scriptInterface,
			Map<String, Object> params) {

		ResultList rl = new ResultList()
		
		try {
			
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
			
				
			SendMessageResponse resp = initClient().sendMessage(from, to, cc, subject, text, html, inlineByteAttachements, inlineFileAttachments)
			
			rl.status = VitalStatus.withOKMessage("Email ID: ${resp.id}, message: ${resp.message}")	
			
		} catch(Exception e) {
			rl.status = VitalStatus.withError(e.localizedMessage)
		}
		
		return rl;
	}
			
	static class MailgunClient {
				
		private HttpClient httpClient;
		
		public final static String apiURL = "https://api.mailgun.net/v2";
		
		private static ObjectMapper mapper = new ObjectMapper();
		
		private String key;
		
		private String domain;
		
		private String urlBase;
		
		public MailgunClient(String domain, String key, boolean multithreaded) {
			this.domain = domain;
			this.key = key;
			if(multithreaded) {
				MultiThreadedHttpConnectionManager multiThreadedHttpConnectionManager = new MultiThreadedHttpConnectionManager();
				httpClient = new HttpClient(multiThreadedHttpConnectionManager);
			} else {
				httpClient = new HttpClient();
			}
			httpClient.getState().setAuthenticationPreemptive(true);
			httpClient.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("api", key));
			
			
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
