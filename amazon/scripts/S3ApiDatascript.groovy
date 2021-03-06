package commons.scripts

import ai.vital.prime.groovy.VitalPrimeGroovyScript
import ai.vital.prime.groovy.VitalPrimeScriptInterface
import ai.vital.query.querybuilder.VitalBuilder
import ai.vital.vitalservice.VitalService;
import ai.vital.vitalservice.VitalStatus;
import ai.vital.vitalservice.query.ResultList
import ai.vital.vitalservice.query.VitalSelectQuery
import ai.vital.vitalsigns.VitalSigns
import ai.vital.vitalsigns.model.VitalSegment
import ai.vital.vitalsigns.model.property.URIProperty
import ai.vital.domain.FileNode
import ai.vital.domain.FileNode_PropertiesHelper

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.CopyObjectResult;
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectResult;

import java.util.Map.Entry
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A datascript that provides simple API based on files stored in S3
 * @author Derek
 *
 */
class S3ApiDatascript implements VitalPrimeGroovyScript {

	private final static Logger log = LoggerFactory.getLogger(S3ApiDatascript.class)
	
	private final static Pattern s3URLPattern = Pattern.compile('^s3\\:\\/\\/([^\\/]+)\\/(.+)$', Pattern.CASE_INSENSITIVE)
	
	static String publicBucketName
	
	static String privateBucketName
	
	static String accessKey
	
	static String secretKey
	
	static AmazonS3Client s3Client
	
	static enum FileScope {
		Public,
		Private
		
		static FileScope fromString(String s) {
			if('public'.equalsIgnoreCase(s)) {
				return Public
			} else if('private'.equalsIgnoreCase(s)) {
				return Private
			} else {
				throw new Exception("Unkown file scope: ${s}")
			}
		}
	}
	
	static class FileURL {
		
		public String getFullURL() {
			return 's3://' + ( bucket ) + '/' + relativePath
		}
		
		String bucket
		
		String relativePath
		
	}
		
	static void init(VitalPrimeScriptInterface scriptInterface) {
		
		if(s3Client == null) {
			
			synchronized (S3ApiDatascript.class) {
				
				if(s3Client == null) {
					String orgID = scriptInterface.getOrganization().organizationID.toString()
					String appID = scriptInterface.getApp().appID.toString()
					Map orgMap = VitalSigns.get().getConfig(orgID)
					if(orgMap == null) throw new Exception("No organization config object: ${orgID}")
					
					Map appMap = orgMap.get(appID)
					if(appMap == null) throw new Exception("No app config object: ${appID}")
					
					Map s3api = appMap.get('s3api')
					if(s3api == null) throw new Exception("No s3api config object for org: ${orgID} app: ${appID}")
					
					publicBucketName = s3api.get('publicBucketName')
					if(!publicBucketName) throw new Exception("No s3api.publicBucketName config param")
					privateBucketName = s3api.get('privateBucketName') 
					if(!privateBucketName) throw new Exception("No s3api.privateBucketName config param")
					
					accessKey = s3api.get('accessKey')
					if(!accessKey) throw new Exception("No s3api.accessKey config param")
					secretKey = s3api.get('secretKey')
					if(!secretKey) throw new Exception("No s3api.secretKey config param")
					
					ClientConfiguration clientConfiguration = new ClientConfiguration()
					
					//short connection and socket timeouts optimized for aimp processing, original values 50000
					clientConfiguration.setConnectionTimeout(10000)
					clientConfiguration.setSocketTimeout(10000)
					
					BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey)
					s3Client = new AmazonS3Client(credentials, clientConfiguration)
					
				}
				
			}
			
		}
		
	}

	@Override
	public ResultList executeScript(VitalPrimeScriptInterface scriptInterface, Map<String, Object> parameters) {

		ResultList rl = new ResultList()
		
		try {
			
			init(scriptInterface)
			
			String action = parameters.get('action')
			
			if(!action) throw new Exception("No action parameter")

			Class<? extends FileNode> fileClass = parameters.get('fileClass')
			if(fileClass == null) throw new Exception("No fileClass parameter")
			
			String externalServiceName = parameters.get('externalServiceName')
			
			String filesSegmentParam = parameters.get('filesSegment')
			if(!filesSegmentParam) throw new Exception("No filesSegment parameter")
			
			String accountURI = parameters.get('accountURI')			
			if(!accountURI) throw new Exception("No accountURI parameter")
			
			String profileURI = parameters.get('profileURI')
			//profile URI is optional
//			if(!profileURI) throw new Exception("No profileURI parameter")
			
			String _scope = parameters.get('scope')
			if(!_scope) throw new Exception("No scope parameter")
			FileScope scope = FileScope.fromString(_scope)
			
			
			//desired path
			String path = parameters.get('path')
			if(!path) throw new Exception("No path parameter")
		
			if(path.startsWith("/")) throw new Exception("Path must not start with slash")
			
			VitalService service = null
			if(externalServiceName != null) {
				service = scriptInterface.getVitalService(externalServiceName)
				if(service == null) throw new Exception("External service not found: ${externalServiceName}")
			}
			
			VitalSegment filesSegment = null
			
			if(service != null) {
				filesSegment = service.getSegment(filesSegmentParam)
				if(filesSegment == null) throw new Exception("Segment not found: ${filesSegmentParam} in external service: ${externalServiceName}")
			} else {
				filesSegment = scriptInterface.getSegment(filesSegmentParam)
				if(filesSegment == null) throw new Exception("Segment not found: ${filesSegmentParam}")
			}
			
			
			//edit should not require paths etc
			if(action == 'edit') {
				
				
				String fileURI = parameters.get('fileURI')
				if(!fileURI) throw new Exception("No fileURI param")
				
				String name = parameters.get('name')
				if(!name) throw new Exception("No name param!")
				
				String fileType = parameters.get('fileType')
//				if(!fileType) throw new Exception("No fileType param")
				
				Map<String> extraProps = parameters.get('extraProps')
				if(extraProps == null) throw new Exception("No extraProps map property")
				
				//select file
				FileNode f = selectFileByURI(scriptInterface, service, filesSegment, fileClass, fileURI)
				if(f == null) throw new Exception("File not found: ${fileURI}")
				
				String s3URL = f.fileURL
				
				
				Matcher matcher = s3URLPattern.matcher(s3URL)
				
				if(!matcher.matches()) {
					throw new Exception("Invalid internal resource URL")
				}
				
				String bucket = matcher.group(1)
				String key = matcher.group(2)
				
				//check if owner has changed
				String currentAccountURI = f.accountURI
				
				String currentProfileURI = f.profileURI
				
				FileScope currentScope = FileScope.fromString(f.fileScope.toString())
				
				String currentPath = f.fileName
				
//				String _scope = parameters.get('scope')
//				if(!_scope) throw new Exception("No scope parameter")
				
				f.name = name
				f.fileType = fileType
				f.timestamp = System.currentTimeMillis()
				
				//these 4 params determine physcial location
				f.fileScope = scope.name()
				f.fileName = path
				f.accountURI = accountURI
				f.profileURI = profileURI
				
				boolean moved = false
				
				if(currentAccountURI != accountURI || currentProfileURI != profileURI || currentScope != scope || currentPath != path) {
					
					//path changes only if scope changes
					FileURL currentURL = fileURL(scriptInterface, service, currentScope, currentAccountURI, currentProfileURI, currentPath)
					FileURL newURL = fileURL(scriptInterface, service, scope, accountURI, profileURI, path)
					
					//first move file, then save node
					s3Client.copyObject(currentURL.bucket, currentURL.relativePath, newURL.bucket, newURL.relativePath)
					
					//delete original location
					s3Client.deleteObject(currentURL.bucket, currentURL.relativePath)
					
					f.fileURL = newURL.getFullURL()
					
					moved = true
					
				}
				
				for(Entry<String, Object> e : extraProps.entrySet()) {
					f.setProperty(e.key, e.value)
				}
				
				if(service != null) {
					service.save(filesSegment, f, true)
				} else {
					scriptInterface.save(filesSegment, f)
				}
				rl.addResult(f)
				rl.status = VitalStatus.withOKMessage("FileNode updated ${moved ? 'and resource moved into another location' : ''}");
				return rl
				
				
			}
			
			
			FileURL fileURL = fileURL(scriptInterface, service, scope, accountURI, profileURI, path)
						
			if(action == 'create') {
				
				FileNode prototype = parameters.get('prototype')
				if(prototype == null) throw new Exception("No prototype (FileNode instance) param")
				
				String base64 = parameters.get('base64')
				byte[] data = parameters.get('data')
				String sourceBucket = parameters.get('sourceBucket')
				String sourceKey = parameters.get('sourceKey')
				
				//arbitrary URL the file would be downloaded from
				String sourceURL = parameters.get('sourceURL')
				String sourceUsername = parameters.get('sourceUsername')
				String sourcePassword = parameters.get('sourcePassword')

				
				//using old files api
				String tempFileURI = parameters.get('tempFileURI')
				String tempFileName = parameters.get('tempFileName')
								
				Boolean deleteOnSuccess = parameters.get('deleteOnSuccess')
				if(deleteOnSuccess == null) deleteOnSuccess = false
				int i= 0
				if(base64) i++
				if(data) i++
				if(sourceBucket || sourceKey) i++
				if(sourceURL) i++
				if(tempFileURI || tempFileName) i++
				
				if(i == 0) throw new Exception("No base64, data, (sourceBucket, sourceKey) nor (tempFileURI, tempFileName) params") 
				
				if( i > 1) throw new Exception("Too many data sources, expected exactly one of: base64, data, sourceURL, (sourceBucket, sourceKey) or (tempFileURI, tempFileName) params")
				
				String name = parameters.get('name')
				if(!name) throw new Exception("No name param!")
				
				String fileType = parameters.get('fileType')
//				if(!fileType) throw new Exception("No fileType param")
				
				Boolean overwrite = parameters.get('overwrite')
				if(overwrite == null) throw new Exception("No overwrite boolean param")

				if(overwrite) {

//					deleteFile(scriptInterface, fileURL);
									
				} else {
				
					List<FileNode> existing = selectFiles(scriptInterface, service, filesSegment, fileClass, fileURL);
				
					if(existing.size() > 0) throw new Exception("File already exists: ${fileURL.fullURL}")
					
				}
				
				if(sourceBucket || sourceKey) {
					
					if(sourceBucket && !sourceKey) throw new Exception("No sourceKey param, required with sourceBucket")
					
					if(!sourceBucket && sourceKey) throw new Exception("No sourceBucket param, required with sourceKey")
					
					//check if exists
					ObjectMetadata om = s3Client.getObjectMetadata(sourceBucket, sourceKey)
					if(om == null) throw new Exception("No object metadata!")
					
				} else if(sourceURL) {
					
				} else if(base64) {
				
					data = Base64.decodeBase64(base64)
				
				} else if(data){
				
				} else if(tempFileName || tempFileURI) {
				
					if(!tempFileURI) throw new Exception("tempFileURI must be provided with tempFileName")
					if(!tempFileName) throw new Exception("tempFileName must be provided with tempFileURI")
				
				}
				
				

				if(overwrite) {
					
					deleteFile(scriptInterface, service, filesSegment, fileClass, fileURL);
					
				}
				
				ObjectMetadata om = new ObjectMetadata()
				
				if(fileType) {
					om.setContentType(fileType)
				}
				
				Long fileLength = null
				
				if(sourceBucket || sourceKey) {
					
					s3Client.copyObject(sourceBucket, sourceKey, fileURL.bucket, fileURL.relativePath)
					
				} else if(sourceURL) {
				

					InputStream inputStream = null
					URLConnection uc = null
					
					try {
						
						if(sourceUsername) {
							
							URL url = new URL(sourceURL);
							uc = url.openConnection();
							String userpass = sourceUsername + ":" + sourcePassword;
							String basicAuth = "Basic " + Base64.encodeBase64String(userpass.getBytes())
							uc.setRequestProperty ("Authorization", basicAuth);
							inputStream = uc.getInputStream();
							
						} else {
						
							inputStream = new URL(sourceURL).openStream()
							
						}
						
						
						s3Client.putObject(fileURL.bucket, fileURL.relativePath, inputStream, om)
												
					} finally {
						IOUtils.closeQuietly(inputStream)
					}
				
				} else if(tempFileURI) {
				
					File tempFile = scriptInterface.getFile(tempFileURI, tempFileName)
					if(tempFile == null) throw new Exception("Temp file not found, URI: ${tempFileURI}, name: ${tempFileName}")
				
					InputStream inputStream = null
					
					try {
						inputStream = new FileInputStream(tempFile)
						s3Client.putObject(fileURL.bucket, fileURL.relativePath, inputStream, om)
					} finally {
						IOUtils.closeQuietly(inputStream)
					}
					
				} else {
				
					om.setContentLength(data.length)
				
					ByteArrayInputStream bis = new ByteArrayInputStream(data)
				
					s3Client.putObject(fileURL.bucket, fileURL.relativePath, bis, om)
					
				}

				ObjectMetadata x = s3Client.getObjectMetadata(fileURL.bucket, fileURL.relativePath)
				fileLength = x.getContentLength()
				
				//create new node
				FileNode newNode = prototype
				newNode.generateURI(service != null ? service.getApp() : scriptInterface.getApp())
				newNode.name = name
				newNode.fileScope = scope.name()
				newNode.fileURL = fileURL.getFullURL()
				newNode.timestamp = System.currentTimeMillis()
				newNode.fileLength = fileLength
				newNode.fileName = path
				newNode.fileType = fileType
				newNode.accountURI = accountURI
				newNode.profileURI = profileURI
				
				if(service != null) {
					service.save(filesSegment, newNode, true)	
				} else {
					scriptInterface.save(filesSegment, newNode)
				}
				
//				if(saveRL.status.status != VitalStatus.Status.ok) throw new Exception("Error when saving new file node: ${saveRL.status.message}")
				
				rl.addResult(newNode)
				
				if((sourceBucket || sourceKey) && deleteOnSuccess.booleanValue()){
					
					s3Client.deleteObject(sourceBucket, sourceKey)
					
				}
				
				if((tempFileURI || tempFileName) && deleteOnSuccess.booleanValue()) {
					scriptInterface.deleteFile(tempFileURI, tempFileName)
				}
				
			} else if(action == 'delete') {
			
				int del = deleteFile(scriptInterface, service, filesSegment, fileClass, fileURL)
				
				rl.status.successes = del
			
				if(del > 0) {
					
					s3Client.deleteObject(fileURL.bucket, fileURL.relativePath)
					
				}
				
			} else if(action == 'get') {
			
				List<FileNode> files = selectFiles(scriptInterface, service, filesSegment, fileClass, fileURL)
				
				for(FileNode f : files) {
					rl.addResult(f);
				}		
			
				rl.totalResults = files.size()
			
			} else {
				throw new Exception("Unknown action: ${action}")
			}
			
		} catch(Exception e) {
			e.printStackTrace()
			rl.status = VitalStatus.withError(e.localizedMessage)
		}
		
		
		return rl;
	}
	
	
	private int deleteFile(VitalPrimeScriptInterface scriptInterface, VitalService service, VitalSegment filesSegment, Class<? extends FileNode> fileClass, FileURL fileURL) throws Exception {
		
		List<FileNode> l = selectFiles(scriptInterface, service, filesSegment, fileClass, fileURL);
		
		if(l.size() == 0) return 0
		List<URIProperty> uris = []
		for(FileNode f : l) {
			uris.add(URIProperty.withString(f.URI))
		}
		
		VitalStatus status = null
		if(service != null) {
			status = service.delete(uris)	
		} else {
			status = scriptInterface.delete(uris)
		}
		
		if(status.status != VitalStatus.Status.ok) {
			throw new Exception("Error when deleting existing file node: ${status.message}")
		}
		
		return l.size()
		
	}
	
	private List<FileNode> selectFiles(VitalPrimeScriptInterface scriptInterface, VitalService service, VitalSegment filesSegment, Class<? extends FileNode> fileClass, FileURL fileURL) throws Exception {
		
		VitalSelectQuery sq = new VitalBuilder().query {
			SELECT {
				value segments: [filesSegment]
				value offset: 0
				value limit: 10
				
				node_constraint { fileClass }
					
				node_constraint { ((FileNode_PropertiesHelper) FileNode.props()).fileURL.equalTo(fileURL.getFullURL()) }
					
			}
		}.toQuery()
		
		ResultList rl = service != null ? service.query(sq) : scriptInterface.query(sq)
		if(rl.status.status != VitalStatus.Status.ok) {
			throw new Exception("Error when selecting existing file nodes: ${rl.status.message}")
		}
		
//		if(rl.results.size() > 1) throw new Exception("More than 1 file with path exists: ${fileURL}") 
		
		List<FileNode> l = []
		
		for(FileNode f : rl.iterator(FileNode.class)) {
			l.add(f)
		}
		
		return l
	}
	
	private FileNode selectFileByURI(VitalPrimeScriptInterface scriptInterface, VitalService service, VitalSegment filesSegment, Class<? extends FileNode> fileClass, String fileURI) {
		
		VitalSelectQuery sq = new VitalBuilder().query {
			
			SELECT {
				
				value segments: [filesSegment]
				value offset: 0
				value limit: 10
				
				node_constraint { fileClass }
				
				node_constraint { "URI eq ${fileURI}"} 
				
			}
			
		}.toQuery()
		
		ResultList rl = null
		
		if(service != null) {
			rl = service.query(sq)
		} else {
			rl = scriptInterface.query(sq)
		}
		
		if(rl.status.status != VitalStatus.Status.ok) {
			throw new Exception("Error when selecting existing file: ${rl.status.message}")
		}
		
		return (FileNode) rl.first()
		
	}
	
	private FileURL fileURL(VitalPrimeScriptInterface scriptInterface, VitalService service, FileScope scope, String accountURI, String profileURI, String path) {
		
		String orgID = service != null ? service.getOrganization().organizationID.toString() : scriptInterface.getOrganization().organizationID.toString()
		String appID = service != null ? service.getApp().appID.toString() : scriptInterface.getApp().appID.toString()
		
		FileURL url = new FileURL()
		url.bucket = scope == FileScope.Public ? publicBucketName : privateBucketName
		
		String profilePart = ''
		if(profileURI != null) {
			profilePart = URLEncoder.encode(profileURI, 'UTF-8') + '/'
		} 
		
		url.relativePath = orgID + '/' + appID + '/' + URLEncoder.encode(accountURI, 'UTF-8') + '/' + profilePart + path
		
		return url
		
		
	}
	
}
