package io.mosip.registrationprocessor.stages.demodedupe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.beans.IntrospectionException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.biometrics.constant.QualityType;
import io.mosip.kernel.biometrics.entities.BDBInfo;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.biometrics.entities.RegistryIDType;
import io.mosip.kernel.core.fsadapter.exception.FSAdapterException;
import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.code.AbisStatusCode;
import io.mosip.registration.processor.core.code.ApiName;
import io.mosip.registration.processor.core.code.EventId;
import io.mosip.registration.processor.core.code.EventName;
import io.mosip.registration.processor.core.code.EventType;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.constant.AbisConstant;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.PacketFiles;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.BiometricRecordValidationException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.RegistrationProcessorCheckedException;
import io.mosip.registration.processor.core.http.ResponseWrapper;
import io.mosip.registration.processor.core.logger.LogDescription;
import io.mosip.registration.processor.core.packet.dto.FieldValue;
import io.mosip.registration.processor.core.packet.dto.FieldValueArray;
import io.mosip.registration.processor.core.packet.dto.Identity;
import io.mosip.registration.processor.core.packet.dto.PacketMetaInfo;
import io.mosip.registration.processor.core.packet.dto.abis.AbisResponseDetDto;
import io.mosip.registration.processor.core.packet.dto.abis.AbisResponseDto;
import io.mosip.registration.processor.core.packet.dto.demographicinfo.DemographicInfoDto;
import io.mosip.registration.processor.core.packet.dto.demographicinfo.IndividualDemographicDedupe;
import io.mosip.registration.processor.core.packet.dto.demographicinfo.JsonValue;
import io.mosip.registration.processor.core.packet.dto.demographicinfo.identify.IdentityJsonValues;
import io.mosip.registration.processor.core.packet.dto.idjson.Document;
import io.mosip.registration.processor.core.spi.packetmanager.PacketInfoManager;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.core.util.RegistrationExceptionMapperUtil;
import io.mosip.registration.processor.packet.storage.dto.ApplicantInfoDto;
import io.mosip.registration.processor.packet.storage.entity.IndividualDemographicDedupeEntity;
import io.mosip.registration.processor.packet.storage.entity.ManualVerificationEntity;
import io.mosip.registration.processor.packet.storage.repository.BasePacketRepository;
import io.mosip.registration.processor.packet.storage.utils.ABISHandlerUtil;
import io.mosip.registration.processor.packet.storage.utils.PriorityBasedPacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.rest.client.audit.dto.AuditResponseDto;
import io.mosip.registration.processor.stages.demodedupe.DemoDedupe;
import io.mosip.registration.processor.stages.demodedupe.DemodedupeProcessor;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.code.RegistrationType;
import io.mosip.registration.processor.status.dao.RegistrationStatusDao;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.entity.RegistrationStatusEntity;
import io.mosip.registration.processor.status.service.RegistrationStatusService;

/**
 * The Class DemodedupeStageTest.
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*","com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", })
@PrepareForTest({ JsonUtil.class, IOUtils.class, HMACUtils2.class })
public class DemodedupeProcessorTest {

	/** The registration status service. */
	@Mock
	private RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	@Mock
	private PacketInfoManager<Identity, ApplicantInfoDto> packetInfoManager;

	/** The manual verfication repository. */
	@Mock
	private BasePacketRepository<ManualVerificationEntity, String> manualVerficationRepository;

	/** The demographic dedupe repository. */
	@Mock
	private BasePacketRepository<IndividualDemographicDedupeEntity, String> demographicDedupeRepository;

	/** The demo dedupe. */
	@Mock
	private DemoDedupe demoDedupe;

	@Mock
	private InputStream inputStream;

	private static final String source = "reg_client";



	/** The dto. */
	private MessageDTO dto = new MessageDTO();

	/** The duplicate dtos. */
	private List<DemographicInfoDto> duplicateDtos = new ArrayList<>();

	/** The audit log request builder. */
	@Mock
	private AuditLogRequestBuilder auditLogRequestBuilder;

	@Mock
	RegistrationExceptionMapperUtil registrationStatusMapperUtil;

	@InjectMocks
	private DemodedupeProcessor demodedupeProcessor;

	@Mock
	private ABISHandlerUtil abisHandlerUtil;

	@Mock
	private RegistrationStatusDao registrationStatusDao;

	@Mock
	private Utilities utility;

	private InternalRegistrationStatusDto registrationStatusDto;

	private InternalRegistrationStatusDto registrationStatusDto1;

	private InternalRegistrationStatusDto registrationStatusDto2;

	private Identity identity = new Identity();

	private PacketMetaInfo packetMetaInfo;

	RegistrationStatusEntity entity = new RegistrationStatusEntity();

	private String stageName = "DemoDedupeStage";

	private IndividualDemographicDedupe individualDemoDedupe;

	@Mock
	LogDescription description;

	@Mock
	private Environment env;

	private static final String DEMODEDUPEENABLE = "mosip.registration.processor.demographic.deduplication.enable";


	@Mock
	private PriorityBasedPacketManagerService packetManagerService;

	/**
	 * Sets the up.
	 *
	 * @throws Exception
	 *             the exception
	 */
	@Before
	public void setUp() throws Exception {
		when(utility.getDefaultSource(any(), any())).thenReturn(source);
		ReflectionTestUtils.setField(demodedupeProcessor, "infantDedupe", "Y");
		ReflectionTestUtils.setField(demodedupeProcessor, "ageLimit", "4");
		ReflectionTestUtils.setField(demodedupeProcessor, "demodedupeInvalidBiometricAction", "RedirectToManualVerification");
		ReflectionTestUtils.setField(demodedupeProcessor, "demodedupeInfantInvalidBiometricAction", "RedirectToManualVerification");
		dto.setRid("2018701130000410092018110735");

		MockitoAnnotations.initMocks(this);

		DemographicInfoDto dto1 = new DemographicInfoDto();
		DemographicInfoDto dto2 = new DemographicInfoDto();
		dto1.setRegId("2018701130000410092018110731");
		dto2.setRegId("2018701130000410092018110732");
		duplicateDtos.add(dto1);
		duplicateDtos.add(dto2);

		registrationStatusDto = new InternalRegistrationStatusDto();
		registrationStatusDto.setRegistrationType("NEW");
		registrationStatusDto.setRegistrationId("2018701130000410092018110735");

		registrationStatusDto1 = new InternalRegistrationStatusDto();
		registrationStatusDto1.setRegistrationType("NEW");
		registrationStatusDto1.setRegistrationId("2018701130000410092018110732");
		registrationStatusDto1.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.IN_PROGRESS.toString());
		registrationStatusDto1.setStatusCode("PROCESSING");
		registrationStatusDto2 = new InternalRegistrationStatusDto();
		registrationStatusDto2.setRegistrationType("NEW");
		registrationStatusDto2.setRegistrationId("2018701130000410092018110731");
		registrationStatusDto2.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.REJECTED.toString());
		registrationStatusDto2.setStatusCode("REJECTED");
		packetMetaInfo = new PacketMetaInfo();

		FieldValue registrationType = new FieldValue();
		registrationType.setLabel("registrationType");
		registrationType.setValue("New");

		FieldValue applicantType = new FieldValue();
		applicantType.setLabel("applicantType");
		applicantType.setValue("Child");

		FieldValue isVerified = new FieldValue();
		isVerified.setLabel("isVerified");
		isVerified.setValue("Verified");

		FieldValue preRegistrationId = new FieldValue();
		preRegistrationId.setLabel("preRegistrationId");
		preRegistrationId.setValue("2018701130000410092018110736");

		entity.setLatestRegistrationTransactionId("5d936385-7ee6-4b51-b21f-c20b0cfbcc11");

		identity.setMetaData(Arrays.asList(registrationType, applicantType, isVerified, preRegistrationId));

		Document documentPob = new Document();
		documentPob.setDocumentCategory("PROOFOFDATEOFBIRTH");
		documentPob.setDocumentName("ProofOfBirth");
		Document document = new Document();
		document.setDocumentCategory("PROOFOFRELATIONSHIP");
		document.setDocumentName("ProofOfRelation");
		List<Document> documents = new ArrayList<Document>();
		documents.add(documentPob);
		documents.add(document);
		individualDemoDedupe = new IndividualDemographicDedupe();

		JsonValue[] jsonArray = new JsonValue[1];
		JsonValue[] jsonArray1 = new JsonValue[1];

		JsonValue jsonValue1 = new JsonValue();
		JsonValue jsonValue2 = new JsonValue();

		jsonValue1.setLanguage("eng");
		jsonValue1.setValue("name");
		jsonArray[0] = jsonValue1;

		jsonValue2.setLanguage("eng");
		jsonValue2.setValue("gender");
		jsonArray1[0] = jsonValue2;
		List<JsonValue[]> jsonArrayList = new ArrayList<>();
		jsonArrayList.add(jsonArray);

		individualDemoDedupe.setName(jsonArrayList);
		individualDemoDedupe.setDateOfBirth("dateOfBirth");
		individualDemoDedupe.setPhone("phone");
		individualDemoDedupe.setEmail("email");
		individualDemoDedupe.setGender(jsonArray1);
		List<FieldValueArray> fieldValueArrayList = new ArrayList<FieldValueArray>();

		FieldValueArray applicantBiometric = new FieldValueArray();
		applicantBiometric.setLabel(PacketFiles.APPLICANTBIOMETRICSEQUENCE.name());
		List<String> applicantBiometricValues = new ArrayList<String>();
		applicantBiometricValues.add(PacketFiles.BOTHTHUMBS.name());
		applicantBiometric.setValue(applicantBiometricValues);
		fieldValueArrayList.add(applicantBiometric);
		FieldValueArray introducerBiometric = new FieldValueArray();
		introducerBiometric.setLabel(PacketFiles.INTRODUCERBIOMETRICSEQUENCE.name());
		List<String> introducerBiometricValues = new ArrayList<String>();
		introducerBiometricValues.add("introducerLeftThumb");
		introducerBiometric.setValue(introducerBiometricValues);
		fieldValueArrayList.add(introducerBiometric);
		FieldValueArray applicantDemographic = new FieldValueArray();
		applicantDemographic.setLabel(PacketFiles.APPLICANTDEMOGRAPHICSEQUENCE.name());
		List<String> applicantDemographicValues = new ArrayList<String>();
		applicantDemographicValues.add(PacketFiles.APPLICANTPHOTO.name());
		applicantDemographicValues.add("ProofOfBirth");
		applicantDemographicValues.add("ProofOfRelation");
		applicantDemographicValues.add("ProofOfAddress");
		applicantDemographicValues.add("ProofOfIdentity");
		applicantDemographic.setValue(applicantDemographicValues);
		fieldValueArrayList.add(applicantDemographic);
		identity.setHashSequence(fieldValueArrayList);
		List<String> sequence2 = new ArrayList<>();
		sequence2.add("audit");
		List<FieldValueArray> fieldValueArrayListSequence = new ArrayList<FieldValueArray>();
		FieldValueArray hashsequence2 = new FieldValueArray();
		hashsequence2.setValue(sequence2);
		fieldValueArrayListSequence.add(hashsequence2);
		identity.setHashSequence2(fieldValueArrayListSequence);
		packetMetaInfo.setIdentity(identity);

		Mockito.when(registrationStatusMapperUtil.getStatusCode(any())).thenReturn("ERROR");

		AuditResponseDto auditResponseDto = new AuditResponseDto();
		ResponseWrapper<AuditResponseDto> responseWrapper = new ResponseWrapper<>();
		responseWrapper.setResponse(auditResponseDto);
		Mockito.doReturn(responseWrapper).when(auditLogRequestBuilder).createAuditRequestBuilder(
				"test case description", EventId.RPR_405.toString(), EventName.UPDATE.toString(),
				EventType.BUSINESS.toString(), "1234testcase", ApiName.AUDIT);
		Mockito.doNothing().when(description).setMessage(any());
		InternalRegistrationStatusDto registrationStatusDto = new InternalRegistrationStatusDto();

		registrationStatusDto.setRetryCount(null);
		Mockito.when(registrationStatusService.getRegistrationStatus(anyString())).thenReturn(registrationStatusDto);
		Mockito.doNothing().when(registrationStatusService).updateRegistrationStatus(any(), any(), any());
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource("RegistrationProcessorIdentity.json").getFile());
		inputStream = new FileInputStream(file);
		String mappingJsonString = IOUtils.toString(inputStream,"UTF-8");
		JSONObject mappingJsonObj= new ObjectMapper().readValue(mappingJsonString, JSONObject.class);
		
		Mockito.when(utility.getRegistrationProcessorMappingJson(anyString())).thenReturn(JsonUtil.getJSONObject(mappingJsonObj, MappingJsonConstants.IDENTITY));
		Mockito.when(packetManagerService.getBiometrics(any(), any(), any(), any(), any()))
		.thenReturn(getBiometricRecord(Arrays.asList("Left Thumb", "Right Thumb", "Left MiddleFinger",
				"Left RingFinger", "Left LittleFinger", "Left IndexFinger", "Right MiddleFinger",
				"Right RingFinger", "Right LittleFinger", "Right IndexFinger", "Left", "Right", "Face"),
				false));
	}

	/**
	 * Test demo dedupe success.
	 *
	 * @throws Exception
	 */
	@Test
	public void testDemoDedupeNewPacketSuccess() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("true");
		byte[] b = "sds".getBytes();
		List<DemographicInfoDto> emptyDuplicateDtoSet = new ArrayList<>();
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);
		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(emptyDuplicateDtoSet);

		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertTrue(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService,Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),any(),any());
		assertEquals(RegistrationTransactionStatusCode.SUCCESS.toString(), argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDemoDedupeUpdatePacketSuccess() throws Exception {
		InternalRegistrationStatusDto registrationStatus = new InternalRegistrationStatusDto();
		registrationStatus.setRegistrationId("2018701130000410092018110736");
		registrationStatus.setRegistrationType(RegistrationType.UPDATE.name());

		IdentityJsonValues identityJsonValues = new IdentityJsonValues();
		identityJsonValues.setValue("fullName");
		when(utility.getDefaultSource(any(), any())).thenReturn(source);
		Mockito.when(utility.getUIn(anyString(), anyString(), any())).thenReturn("2345");

		JSONArray arr = new JSONArray();
		arr.add("name");

		JSONObject obj = new JSONObject();
		obj.put("fullName", arr);
		Mockito.when(utility.retrieveIdrepoJson(anyString())).thenReturn(obj);

		byte[] b = "sds".getBytes();
		List<DemographicInfoDto> emptyDuplicateDtoSet = new ArrayList<>();

		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatus);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(emptyDuplicateDtoSet);
		Mockito.when(packetInfoManager.getIdentityKeysAndFetchValuesFromJSON(any(),any(),any())).thenReturn(individualDemoDedupe);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.POST_ABIS_IDENTIFICATION);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertTrue(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.SUCCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());

	}
	
	@Test
	public void testDemoDedupeUpdatePacketException() throws Exception {
		InternalRegistrationStatusDto registrationStatus = new InternalRegistrationStatusDto();
		registrationStatus.setRegistrationId("2018701130000410092018110736");
		registrationStatus.setRegistrationType(RegistrationType.UPDATE.name());

		IdentityJsonValues identityJsonValues = new IdentityJsonValues();
		identityJsonValues.setValue("fullName");
		when(utility.getDefaultSource(any(),any())).thenReturn(source);
		Mockito.when(utility.getUIn(anyString(), anyString(), any())).thenReturn("2345");
		
		Mockito.when(utility.retrieveIdrepoJson(any())).thenReturn(null);

		byte[] b = "sds".getBytes();
		List<DemographicInfoDto> emptyDuplicateDtoSet = new ArrayList<>();

		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatus);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(emptyDuplicateDtoSet);
		Mockito.when(packetInfoManager.getIdentityKeysAndFetchValuesFromJSON(any(),any(),any())).thenReturn(individualDemoDedupe);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertFalse(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.ERROR.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.FAILED.toString(), argument.getAllValues().get(0).getStatusCode());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testDemoDedupeResUpdatePacketSuccess() throws Exception {
		InternalRegistrationStatusDto registrationStatus = new InternalRegistrationStatusDto();
		registrationStatus.setRegistrationId("2018701130000410092018110736");
		registrationStatus.setRegistrationType(RegistrationType.RES_UPDATE.name());

		IdentityJsonValues identityJsonValues = new IdentityJsonValues();
		identityJsonValues.setValue("fullName");
		when(utility.getDefaultSource(any(),any())).thenReturn(source);
		Mockito.when(utility.getUIn(anyString(), anyString(), any())).thenReturn("2345");

		JSONArray arr = new JSONArray();
		arr.add("name");

		JSONObject obj = new JSONObject();
		obj.put("fullName", arr);
		Mockito.when(utility.retrieveIdrepoJson(anyString())).thenReturn(obj);

		byte[] b = "sds".getBytes();
		List<DemographicInfoDto> emptyDuplicateDtoSet = new ArrayList<>();

		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.getJSONValue(any(), any())).thenReturn("Test1,Test2");
		PowerMockito.when(JsonUtil.getJSONObject(any(), any())).thenReturn(obj);
		JsonValue[] nameArray = new JsonValue[1];
		JsonValue jsonValue = new JsonValue();
		jsonValue.setLanguage("eng");
		jsonValue.setValue("test");
		nameArray[0] = jsonValue;
		PowerMockito.when(JsonUtil.getJsonValues(any(),any())).thenReturn(nameArray);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatus);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(emptyDuplicateDtoSet);
		IndividualDemographicDedupe demographicData = individualDemoDedupe;
		demographicData.setName(null);
		demographicData.setDateOfBirth(null);
		demographicData.setGender(null);
		demographicData.setPhone(null);
		demographicData.setEmail(null);
		Mockito.when(packetInfoManager.getIdentityKeysAndFetchValuesFromJSON(any(),any(), any())).thenReturn(demographicData);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.POST_ABIS_IDENTIFICATION);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertTrue(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.SUCCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDemoDedupeSuccessNotDuplicateAfterAuth() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("true");
		byte[] b = "sds".getBytes();
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);
		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		registrationStatusDto.setRegistrationType(RegistrationType.NEW.toString());
		Mockito.when(registrationStatusDao.findById(any())).thenReturn(entity);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertTrue(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.SUCCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());

	}

	/**
	 * Test demo dedupe potential match.
	 *
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testDemoDedupePotentialMatchWithEmpty() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("true");
		List<AbisResponseDto> abisResponseDtos = new ArrayList<>();
		AbisResponseDto abisResponseDto = new AbisResponseDto();
		abisResponseDto.setId("100");
		abisResponseDto.setStatusCode(AbisStatusCode.SUCCESS.toString());
		abisResponseDto.setLangCode("eng");
		abisResponseDtos.add(abisResponseDto);

		byte[] b = "sds".getBytes();

		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.POST_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		registrationStatusDto.setRegistrationType(RegistrationType.NEW.toString());
		Mockito.when(registrationStatusDao.findById(any())).thenReturn(entity);
		Mockito.when(packetInfoManager.getAbisResponseRecords(anyString(), anyString())).thenReturn(abisResponseDtos);
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);

		assertTrue(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.SUCCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDemoDedupePotentialMatchSuccess() throws Exception {
		List<AbisResponseDto> abisResponseDtos = new ArrayList<>();
		List<AbisResponseDetDto> abisResponseDetDtos = new ArrayList<>();
		List<String> matchedRegIds = new ArrayList<>();
		AbisResponseDto abisResponseDto = new AbisResponseDto();
		abisResponseDto.setId("100");
		abisResponseDto.setStatusCode(AbisStatusCode.SUCCESS.toString());
		abisResponseDto.setLangCode("eng");
		abisResponseDtos.add(abisResponseDto);

		AbisResponseDetDto abisResponseDetDto = new AbisResponseDetDto();
		abisResponseDetDto.setAbiRespId("100");
		abisResponseDetDtos.add(abisResponseDetDto);

		matchedRegIds.add("2018701130000410092018110735");
		byte[] b = "sds".getBytes();

		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.POST_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		registrationStatusDto.setRegistrationType(RegistrationType.NEW.toString());
		Mockito.when(registrationStatusDao.findById(any())).thenReturn(entity);
		Mockito.when(packetInfoManager.getAbisResponseRecords(anyString(), anyString())).thenReturn(abisResponseDtos);
		Mockito.when(packetInfoManager.getAbisResponseDetRecordsList(any())).thenReturn(abisResponseDetDtos);
		Mockito.when(abisHandlerUtil.getUniqueRegIds(any(), any(), any())).thenReturn(matchedRegIds);
		doNothing().when(packetInfoManager).saveManualAdjudicationData(anyList(), anyString(), any(), any(), any(),
				any(), any());
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);

		assertFalse(messageDto.getIsValid());
		assertEquals(MessageBusAddress.MANUAL_VERIFICATION_BUS_IN, messageDto.getMessageBusAddress());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.FAILED.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());
	}
	
	@Test
	public void testDemoDedupeEmptyMatch() throws Exception {
		List<AbisResponseDto> abisResponseDtos = new ArrayList<>();
		List<AbisResponseDetDto> abisResponseDetDtos = new ArrayList<>();
		List<String> matchedRegIds = new ArrayList<>();
		AbisResponseDto abisResponseDto = new AbisResponseDto();
		abisResponseDto.setId("100");
		abisResponseDto.setStatusCode(AbisStatusCode.SUCCESS.toString());
		abisResponseDto.setLangCode("eng");
		abisResponseDtos.add(abisResponseDto);

		AbisResponseDetDto abisResponseDetDto = new AbisResponseDetDto();
		abisResponseDetDto.setAbiRespId("100");
		abisResponseDetDtos.add(abisResponseDetDto);
		
		byte[] b = "sds".getBytes();

		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.POST_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		registrationStatusDto.setRegistrationType(RegistrationType.NEW.toString());
		Mockito.when(registrationStatusDao.findById(any())).thenReturn(entity);
		Mockito.when(packetInfoManager.getAbisResponseRecords(anyString(), anyString())).thenReturn(abisResponseDtos);
		Mockito.when(packetInfoManager.getAbisResponseDetRecordsList(any())).thenReturn(abisResponseDetDtos);
		Mockito.when(abisHandlerUtil.getUniqueRegIds(any(), any(), any())).thenReturn(matchedRegIds);
		doNothing().when(packetInfoManager).saveManualAdjudicationData(anyList(), anyString(), any(), any(), any(),
				any(), any());
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);

		assertTrue(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.SUCCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDemoDedupePotentialMatchAbisResponseNotProcessed() throws Exception {
		List<AbisResponseDto> abisResponseDtos = new ArrayList<>();
		AbisResponseDto abisResponseDto = new AbisResponseDto();
		abisResponseDto.setId("100");
		abisResponseDto.setStatusCode("ERROR");
		abisResponseDto.setLangCode("eng");
		abisResponseDtos.add(abisResponseDto);

		byte[] b = "sds".getBytes();

		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.POST_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		registrationStatusDto.setRegistrationType(RegistrationType.NEW.toString());
		Mockito.when(registrationStatusDao.findById(any())).thenReturn(entity);
		Mockito.when(packetInfoManager.getAbisResponseRecords(anyString(), anyString())).thenReturn(abisResponseDtos);
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);
		Mockito.when(registrationStatusMapperUtil.getStatusCode(any())).thenReturn("REPROCESS");
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);

		assertFalse(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.REPROCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());

	}

	/**
	 * Test demo dedupe failure.
	 *
	 * @throws ApisResourceAccessException           the apis resource access
	 *                                               exception
	 * @throws IOException                           Signals that an I/O exception
	 *                                               has occurred.
	 * @throws IntrospectionException
	 * @throws ParseException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws                                       io.mosip.kernel.core.exception.IOException
	 * @throws RegistrationProcessorCheckedException
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testDemoDedupeFailure() throws ApisResourceAccessException, IOException, JsonProcessingException, PacketManagerException, PacketManagerException {
		InternalRegistrationStatusDto registrationStatusDto = new InternalRegistrationStatusDto();

		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(RegistrationType.NEW.toString());
		Mockito.when(registrationStatusService.getRegistrationStatus(anyString())).thenReturn(registrationStatusDto);
		registrationStatusDto.setRetryCount(3);
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertTrue(messageDto.getInternalError());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.ERROR.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.FAILED.toString(), argument.getAllValues().get(0).getStatusCode());

	}

	/**
	 * Test resource exception.
	 *
	 * @throws ApisResourceAccessException           the apis resource access
	 *                                               exception
	 * @throws IOException                           Signals that an I/O exception
	 *                                               has occurred.
	 * @throws IntrospectionException
	 * @throws ParseException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws                                       io.mosip.kernel.core.exception.IOException
	 * @throws RegistrationProcessorCheckedException
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testResourceException() throws ApisResourceAccessException, IOException, JsonProcessingException, PacketManagerException {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("true");

		List<DemographicInfoDto> emptyDuplicateDtoSet = new ArrayList<>();
		when(utility.getDefaultSource(any(),any())).thenReturn(source);
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);

		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(emptyDuplicateDtoSet);
		when(utility.getDefaultSource(any(), any())).thenReturn(source);
		ApisResourceAccessException exp = new ApisResourceAccessException("errorMessage");
		Mockito.doThrow(exp).when(utility).getApplicantAge(anyString(),anyString(), any());
		Mockito.when(registrationStatusMapperUtil.getStatusCode(any())).thenReturn("REPROCESS");

		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertEquals(true, messageDto.getInternalError());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.REPROCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());
	}

	@Test
	public void testFSAdapterExceptionException()
			throws ApisResourceAccessException, IOException, JsonProcessingException, PacketManagerException {
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);

		FSAdapterException exp = new FSAdapterException("errorMessage", "test");
		Mockito.doThrow(exp).when(abisHandlerUtil).getPacketStatus(any());
		registrationStatusDto.setRegistrationType(RegistrationType.NEW.toString());
		Mockito.when(registrationStatusService.getRegistrationStatus(anyString())).thenReturn(registrationStatusDto);
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);
		Mockito.when(registrationStatusMapperUtil.getStatusCode(any())).thenReturn("REPROCESS");
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertEquals(true, messageDto.getInternalError());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.REPROCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());
	}

	@Test
	public void testIllegalArgumentException() throws ApisResourceAccessException, IOException, JsonProcessingException, PacketManagerException {
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		IllegalArgumentException exp = new IllegalArgumentException("errorMessage");
		Mockito.doThrow(exp).when(abisHandlerUtil).getPacketStatus(any());
		registrationStatusDto.setRegistrationType(RegistrationType.NEW.toString());
		Mockito.when(registrationStatusService.getRegistrationStatus(anyString())).thenReturn(registrationStatusDto);
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertEquals(true, messageDto.getInternalError());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.ERROR.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.FAILED.toString(), argument.getAllValues().get(0).getStatusCode());
	}

	/**
	 * Test demo dedupe success.
	 *
	 * @throws Exception
	 */
	@Test
	public void testDemoDedupeNewPacketSkipped() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("false");
		byte[] b = "sds".getBytes();


		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertTrue(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.SUCCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());

	}
	/**
	 * Test demo dedupe success.
	 *
	 * @throws Exception
	 */
	@Test
	public void testDemoDedupeNewPacketSuccessWithDuplicates() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("true");
		byte[] b = "sds".getBytes();
	

		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto).thenReturn(registrationStatusDto1).thenReturn(registrationStatusDto2);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);
		List<String> matchedRidsWithoutRejected = new ArrayList<>();
		matchedRidsWithoutRejected.add("2018701130000410092018110731");
		Mockito.when(abisHandlerUtil.removeRejectedIds(anyList()))
				.thenReturn(matchedRidsWithoutRejected);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertFalse(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.IN_PROGRESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());

	}
	/**
	 * Test demo dedupe success.
	 *
	 * @throws Exception
	 */
	@Test
	public void testDemoDedupeNewPacketSuccessWithDuplicatesWithException() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("true");
		byte[] b = "sds".getBytes();
	

		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		List<String> matchedRidsWithoutRejected = new ArrayList<>();
		matchedRidsWithoutRejected.add("2018701130000410092018110731");
		Mockito.when(abisHandlerUtil.removeRejectedIds(anyList()))
				.thenReturn(matchedRidsWithoutRejected);
		NullPointerException exp=new NullPointerException();
		Mockito.doThrow(exp).when(packetInfoManager).saveDemoDedupePotentialData(any(),anyString(),anyString());
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(20);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertFalse(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.REPROCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());


	}

	@Test
	public void testDemoDedupeNewChildPacketSuccess() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("true");
		byte[] b = "sds".getBytes();
		List<DemographicInfoDto> emptyDuplicateDtoSet = new ArrayList<>();
		Mockito.when(utility.getApplicantAge(anyString(),anyString(), any())).thenReturn(2);
		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(emptyDuplicateDtoSet);

		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertTrue(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.SUCCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());


	}
	
	@Test
	public void testDemoDedupeDisabledSuccess() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("false");
		byte[] b = "sds".getBytes();
		List<DemographicInfoDto> emptyDuplicateDtoSet = new ArrayList<>();
		Mockito.when(utility.getApplicantAge(any(),any(), any())).thenReturn(20);
		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(emptyDuplicateDtoSet);

		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertTrue(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.SUCCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());

	}
	
	@Test
	public void testDemoDedupeSuccess() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("true");
		byte[] b = "sds".getBytes();
		List<DemographicInfoDto> emptyDuplicateDtoSet = new ArrayList<>();
		Mockito.when(utility.getApplicantAge(any(), any(), any())).thenReturn(20);
		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(emptyDuplicateDtoSet);

		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertTrue(messageDto.getIsValid());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService, Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),
				any(), any());
		assertEquals(RegistrationTransactionStatusCode.SUCCESS.toString(),
				argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());

	}
	@Test
	public void testDemoDedupeNewDuplicatePacketSuccess() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("true");
		
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.DUPLICATE_FOR_SAME_TRANSACTION_ID);

		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertTrue(messageDto.getInternalError());

	}

	private BiometricRecord getBiometricRecord(List<String> bioAttributes, boolean isBdbEmpty) {
		BiometricRecord biometricRecord = new BiometricRecord();

		byte[] bdb = isBdbEmpty ? null : new byte[2048];
		for (String bioAttribute : bioAttributes) {
			BIR birType1 = new BIR.BIRBuilder().build();
			BDBInfo bdbInfoType1 = new BDBInfo.BDBInfoBuilder().build();
			io.mosip.kernel.biometrics.entities.RegistryIDType registryIDType = new RegistryIDType();
			registryIDType.setOrganization("Mosip");
			registryIDType.setType("257");
			io.mosip.kernel.biometrics.constant.QualityType quality = new QualityType();
			quality.setAlgorithm(registryIDType);
			quality.setScore(90l);
			bdbInfoType1.setQuality(quality);

			BiometricType singleType1 = bioAttribute.equalsIgnoreCase("face") ? BiometricType.FACE
					: bioAttribute.equalsIgnoreCase("left") || bioAttribute.equalsIgnoreCase("right")
							? BiometricType.IRIS
							: BiometricType.FINGER;
			List<BiometricType> singleTypeList1 = new ArrayList<>();
			singleTypeList1.add(singleType1);
			bdbInfoType1.setType(singleTypeList1);

			String[] bioAttributeArray = bioAttribute.split(" ");

			List<String> subtype = new ArrayList<>();
			for (String attribute : bioAttributeArray) {
				subtype.add(attribute);
			}
			bdbInfoType1.setSubtype(subtype);

			birType1.setBdbInfo(bdbInfoType1);
			birType1.setBdb(bdb);

			biometricRecord.getSegments().add(birType1);
		}

		return biometricRecord;
	}

	@Test
	public void testDemoDedupeNewPacketWithDuplicatesWithNoBiometrics() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("true");
		byte[] b = "sds".getBytes();

		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto).thenReturn(registrationStatusDto1).thenReturn(registrationStatusDto2);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		Mockito.when(packetManagerService.getBiometrics(any(), any(), any(), any(), any())).thenReturn(null);
		BiometricRecordValidationException e = new BiometricRecordValidationException("Biometirc validation failed");
		Mockito.doThrow(e).when(abisHandlerUtil).validateBiometricRecord(any(), anyList());
		List<String> matchedRidsWithoutRejected = new ArrayList<>();
		matchedRidsWithoutRejected.add("2018701130000410092018110731");
		Mockito.when(abisHandlerUtil.removeRejectedIds(anyList()))
				.thenReturn(matchedRidsWithoutRejected);
		Mockito.when(utility.getApplicantAge(anyString(), anyString(), any())).thenReturn(20);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertEquals(MessageBusAddress.MANUAL_VERIFICATION_BUS_IN, messageDto.getMessageBusAddress());
		assertEquals(false, messageDto.getIsValid());
		assertEquals(false, messageDto.getInternalError());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService,Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),any(),any());
		assertEquals(RegistrationTransactionStatusCode.FAILED.toString(), argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());

	}

	@Test
	public void testDemoDedupeNewPacketWithDuplicatesWithNoBiometricsWithDemodedupeSuccess() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("true");
		byte[] b = "sds".getBytes();
		ReflectionTestUtils.setField(demodedupeProcessor, "demodedupeInvalidBiometricAction",
				"MarkAsDemodedupeSuccess");
		ReflectionTestUtils.setField(demodedupeProcessor, "demodedupeInfantInvalidBiometricAction", "MarkAsDemodedupeSuccess");
		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto).thenReturn(registrationStatusDto1).thenReturn(registrationStatusDto2);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		Mockito.when(packetManagerService.getBiometrics(any(), any(), any(), any(), any())).thenReturn(null);
		BiometricRecordValidationException e = new BiometricRecordValidationException("Biometirc validation failed");
		Mockito.doThrow(e).when(abisHandlerUtil).validateBiometricRecord(any(), anyList());
		List<String> matchedRidsWithoutRejected = new ArrayList<>();
		matchedRidsWithoutRejected.add("2018701130000410092018110731");
		Mockito.when(abisHandlerUtil.removeRejectedIds(anyList()))
				.thenReturn(matchedRidsWithoutRejected);
		Mockito.when(utility.getApplicantAge(anyString(), anyString(), any())).thenReturn(20);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertTrue(messageDto.getIsValid());
		assertEquals(false, messageDto.getInternalError());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService,Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),any(),any());
		assertEquals(RegistrationTransactionStatusCode.SUCCESS.toString(), argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument.getAllValues().get(0).getStatusCode());

	}

	@Test
	public void testDemoDedupeNewPacketWithDuplicatesWithNoBiometricsWithDemodedupeRejected() throws Exception {
		when(env.getProperty(DEMODEDUPEENABLE)).thenReturn("true");
		byte[] b = "sds".getBytes();
		ReflectionTestUtils.setField(demodedupeProcessor, "demodedupeInvalidBiometricAction",
				"MarkAsDemodedupeRejected");
		ReflectionTestUtils.setField(demodedupeProcessor, "demodedupeInfantInvalidBiometricAction", "MarkAsDemodedupeRejected");
		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto).thenReturn(registrationStatusDto1).thenReturn(registrationStatusDto2);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		Mockito.when(packetManagerService.getBiometrics(any(), any(), any(), any(), any())).thenReturn(null);
		BiometricRecordValidationException e = new BiometricRecordValidationException("Biometirc validation failed");
		Mockito.doThrow(e).when(abisHandlerUtil).validateBiometricRecord(any(), anyList());
		List<String> matchedRidsWithoutRejected = new ArrayList<>();
		matchedRidsWithoutRejected.add("2018701130000410092018110731");
		Mockito.when(abisHandlerUtil.removeRejectedIds(anyList()))
				.thenReturn(matchedRidsWithoutRejected);
		Mockito.when(utility.getApplicantAge(anyString(), anyString(), any())).thenReturn(20);
		MessageDTO messageDto = demodedupeProcessor.process(dto, stageName);
		assertFalse(messageDto.getIsValid());
		assertEquals(false, messageDto.getInternalError());
		ArgumentCaptor<InternalRegistrationStatusDto> argument = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService,Mockito.atLeastOnce()).updateRegistrationStatus(argument.capture(),any(),any());
		assertEquals(RegistrationTransactionStatusCode.FAILED.toString(), argument.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.REJECTED.toString(), argument.getAllValues().get(0).getStatusCode());
	}

	@Test
	public void testDemoDedupeNewInfantPacketWithDuplicatesWithNoBiometrics() throws Exception {
		byte[] b = "sds".getBytes();
		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto).thenReturn(registrationStatusDto1).thenReturn(registrationStatusDto2);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		Mockito.when(packetManagerService.getBiometrics(any(), any(), any(), any(), any())).thenReturn(null);
		BiometricRecordValidationException e = new BiometricRecordValidationException("Biometirc validation failed");
		Mockito.doThrow(e).when(abisHandlerUtil).validateBiometricRecord(any(), anyList());
		List<String> matchedRidsWithoutRejected = new ArrayList<>();
		matchedRidsWithoutRejected.add("2018701130000410092018110731");
		Mockito.when(abisHandlerUtil.removeRejectedIds(anyList()))
				.thenReturn(matchedRidsWithoutRejected);
		Mockito.when(utility.getApplicantAge(anyString(), anyString(), any())).thenReturn(1);
		MessageDTO messageDto1 = demodedupeProcessor.process(dto, stageName);
		assertEquals(MessageBusAddress.MANUAL_VERIFICATION_BUS_IN, messageDto1.getMessageBusAddress());
		assertEquals(false, messageDto1.getIsValid());
		assertEquals(false, messageDto1.getInternalError());
		ArgumentCaptor<InternalRegistrationStatusDto> argument1 = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService,Mockito.atLeastOnce()).updateRegistrationStatus(argument1.capture(),any(),any());
		assertEquals(RegistrationTransactionStatusCode.FAILED.toString(), argument1.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument1.getAllValues().get(0).getStatusCode());

	}

	@Test
	public void testDemoDedupeNewInfantPacketWithDuplicatesWithNoBiometricsWithDemodedupeSuccess() throws Exception {
		byte[] b = "sds".getBytes();
		ReflectionTestUtils.setField(demodedupeProcessor, "demodedupeInfantInvalidBiometricAction", "MarkAsDemodedupeSuccess");
		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto).thenReturn(registrationStatusDto1).thenReturn(registrationStatusDto2);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		Mockito.when(packetManagerService.getBiometrics(any(), any(), any(), any(), any())).thenReturn(null);
		BiometricRecordValidationException e = new BiometricRecordValidationException("Biometirc validation failed");
		Mockito.doThrow(e).when(abisHandlerUtil).validateBiometricRecord(any(), anyList());
		List<String> matchedRidsWithoutRejected = new ArrayList<>();
		matchedRidsWithoutRejected.add("2018701130000410092018110731");
		Mockito.when(abisHandlerUtil.removeRejectedIds(anyList())).thenReturn(matchedRidsWithoutRejected);
		Mockito.when(utility.getApplicantAge(anyString(), anyString(), any())).thenReturn(1);
		MessageDTO messageDto1 = demodedupeProcessor.process(dto, stageName);
		assertTrue(messageDto1.getIsValid());
		assertEquals(false, messageDto1.getInternalError());
		ArgumentCaptor<InternalRegistrationStatusDto> argument1 = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService,Mockito.atLeastOnce()).updateRegistrationStatus(argument1.capture(),any(),any());
		assertEquals(RegistrationTransactionStatusCode.SUCCESS.toString(), argument1.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.PROCESSING.toString(), argument1.getAllValues().get(0).getStatusCode());

	}

	@Test
	public void testDemoDedupeNewInfantPacketWithDuplicatesWithNoBiometricsWithDemodedupeRejected() throws Exception {
		byte[] b = "sds".getBytes();
		ReflectionTestUtils.setField(demodedupeProcessor, "demodedupeInfantInvalidBiometricAction", "MarkAsDemodedupeRejected");
		PowerMockito.mockStatic(JsonUtil.class);
		PowerMockito.mockStatic(IOUtils.class);
		PowerMockito.when(JsonUtil.class, "inputStreamtoJavaObject", inputStream, PacketMetaInfo.class)
				.thenReturn(packetMetaInfo);
		PowerMockito.when(IOUtils.class, "toByteArray", inputStream).thenReturn(b);
		Mockito.when(registrationStatusService.getRegistrationStatus(any())).thenReturn(registrationStatusDto).thenReturn(registrationStatusDto1).thenReturn(registrationStatusDto2);
		Mockito.when(abisHandlerUtil.getPacketStatus(any())).thenReturn(AbisConstant.PRE_ABIS_IDENTIFICATION);
		Mockito.when(demoDedupe.performDedupe(anyString())).thenReturn(duplicateDtos);
		Mockito.when(packetManagerService.getBiometrics(any(), any(), any(), any(), any())).thenReturn(null);
		BiometricRecordValidationException e = new BiometricRecordValidationException("Biometirc validation failed");
		Mockito.doThrow(e).when(abisHandlerUtil).validateBiometricRecord(any(), anyList());
		List<String> matchedRidsWithoutRejected = new ArrayList<>();
		matchedRidsWithoutRejected.add("2018701130000410092018110731");
		Mockito.when(abisHandlerUtil.removeRejectedIds(anyList())).thenReturn(matchedRidsWithoutRejected);
		Mockito.when(utility.getApplicantAge(anyString(), anyString(), any())).thenReturn(1);
		MessageDTO messageDto1 = demodedupeProcessor.process(dto, stageName);
		assertFalse(messageDto1.getIsValid());
		assertEquals(false, messageDto1.getInternalError());
		ArgumentCaptor<InternalRegistrationStatusDto> argument1 = ArgumentCaptor
				.forClass(InternalRegistrationStatusDto.class);
		Mockito.verify(registrationStatusService,Mockito.atLeastOnce()).updateRegistrationStatus(argument1.capture(),any(),any());
		assertEquals(RegistrationTransactionStatusCode.FAILED.toString(), argument1.getAllValues().get(0).getLatestTransactionStatusCode());
		assertEquals(RegistrationStatusCode.REJECTED.toString(), argument1.getAllValues().get(0).getStatusCode());	

	}
}