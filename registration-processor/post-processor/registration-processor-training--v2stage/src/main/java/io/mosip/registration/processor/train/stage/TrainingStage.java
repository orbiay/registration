package io.mosip.registration.processor.train.stage;


import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.registration.processor.core.abstractverticle.*;
import io.mosip.registration.processor.core.code.RegistrationTransactionTypeCode;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.packet.storage.dto.BiometricType;
import io.mosip.registration.processor.packet.storage.helper.PacketManagerHelper;
import io.mosip.registration.processor.packet.storage.utils.PriorityBasedPacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.service.RegistrationStatusService;
import org.json.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
public class TrainingStage extends MosipVerticleAPIManager {
    @Value("${server.port}")
    private String port;

    @Value("${vertx.cluster.configuration}")
    private String clusterManagerUrl;

    @Autowired
    private RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

    /** The mosip event bus. */
    private MosipEventBus mosipEventBus;
    /** worker pool size. */
    @Autowired
    private  PacketManagerHelper packetManagerHelper;
    @Value("${worker.pool.size}")
    private Integer workerPoolSize;
    /** After this time intervel, message should be considered as expired (In seconds). */
    @Value("${mosip.regproc.printing.message.expiry-time-limit}")
    private Long messageExpiryTimeLimit;

    @Autowired
    private PriorityBasedPacketManagerService priorityBasedPacketManagerService;

    /** Mosip router for APIs */
    @Autowired
    MosipRouter router;

    @Autowired
    private Utilities utilities;

    @Override
    public MessageDTO process(MessageDTO object) {
        System.out.println("------------------ >>>>> Object is " + object);
        try {
            System.out.println("------------------------------------------");
            String RID = object.getRid();
            JSONObject Packet = utilities.retrieveUIN(RID);
            System.out.println(Packet);
            Object gender =  Packet.get("gender");
            Object city = Packet.get("city");
            Object fullName = Packet.get("fullName");
            Object dateOfBirth = Packet.get("dateOfBirth");
            Object phone = Packet.get("phone");
            Object addressLine1 = Packet.get("addressLine1");
            Object individualBiometrics = Packet.get("individualBiometrics");
            System.out.println(gender + " -------------------------------------" );
            InternalRegistrationStatusDto registrationStatusDto = registrationStatusService.getRegistrationStatus(object.getRid());
            List<String> modalities = new ArrayList<>();
            modalities.add(BiometricType.FACE.value());
            BiometricRecord individualBiometricsObject = priorityBasedPacketManagerService.getBiometrics(
                    object.getRid(), MappingJsonConstants.INDIVIDUAL_BIOMETRICS, modalities, registrationStatusDto.getRegistrationType(), ProviderStageName.QUALITY_CHECKER);
            System.out.println( individualBiometricsObject);
            List<String> Fields ;

            String Gender = priorityBasedPacketManagerService.getField(RID,"gender",registrationStatusDto.getRegistrationType(),ProviderStageName.QUALITY_CHECKER);
            System.out.println(Gender + " -----------------------------------------------");
        }catch (Exception e)
        {
//            String bioField = priorityBasedPacketManagerService.getFieldByMappingJsonKey(registrationStatusDto.getRegistrationId(),
//                    MappingJsonConstants.INDIVIDUAL_BIOMETRICS, registrationStatusDto.getRegistrationType(), ProviderStageName.BIO_DEDUPE);
        }
        return object;
    }

    public void deployVerticle() {

        mosipEventBus = this.getEventBus(this, clusterManagerUrl, workerPoolSize);
        this.consumeAndSend(mosipEventBus, MessageBusAddress.TRAINING_BUS_IN, MessageBusAddress.TRAINIG_BUS_OUT,messageExpiryTimeLimit);

    }
}
