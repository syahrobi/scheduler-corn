package id.teravin.ifc.config;

import com.teravin.base.util.DateUtils;
import id.teravin.ifc.dto.BuildingTemporaryDto;
import id.teravin.ifc.dto.EmployeeResponseDto;
import id.teravin.ifc.model.EmployeeWfoMaster;
import id.teravin.ifc.service.BuildingTemporaryService;
import id.teravin.ifc.service.EmployeeWfoMasterService;
import id.teravin.ifc.service.EmployeeWfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class EmployeeScheduling {

    @Autowired
    private RestTemplateProperties restTemplateProperties;

    @Autowired
    private RestTemplateConfig restTemplateConfig;

    @Autowired
    private RabbitMqSender rabbitMqSender;

    @Autowired
    private BuildingTemporaryService buildingTemporaryService;

    @Autowired
    private EmployeeWfoService employeeWfoService;

    @Autowired
    private EmployeeWfoMasterService employeeWfoMasterService;

    /** 01:01 am **/
    @Scheduled(cron = "${corn.employee.scheduler}")
    public void getEmployeeData() {

        Map map = new HashMap();
        Date date = new Date();

        String url = restTemplateProperties.getHostEmployeeReservation() + restTemplateProperties.getUrlGetTemplateEmployeeReservation();
        ResponseEntity<EmployeeResponseDto> responseEntity = restTemplateConfig.getRestTemplate().postForEntity(url, map, EmployeeResponseDto.class);
        log.info("== response {} ==", responseEntity.getBody());

        List list = responseEntity.getBody().getDatakaryawanreservation();

        buildingTemporaryService.delete();

        if(list.isEmpty()) {
            log.info("== data is empty ==");
        }

        try {
            for (int i=0; i < list.size(); i++) {
                BuildingTemporaryDto dto = new BuildingTemporaryDto();

                HashMap<String, String> passedValues = (HashMap<String, String>) responseEntity.getBody().getDatakaryawanreservation().get(i);
                for (Map.Entry mapTemp : passedValues.entrySet()) {

                    if(mapTemp.getKey().equals("NIK")) {
                        dto.setNik(mapTemp.getValue().toString());
                    }
                    if(mapTemp.getKey().equals("Email")) {
                        dto.setEmail(mapTemp.getValue().toString());
                    }
                    if(mapTemp.getKey().equals("DivisionDescription")) {
                        dto.setDivisionDescription(mapTemp.getValue().toString());
                    }
                    if(mapTemp.getKey().equals("DefaultFloor")) {
                        dto.setDefaultFloor(mapTemp.getValue().toString());
                        String dataKaryawan[] = mapTemp.getValue().toString().split("#");
                        splitBuildingFloor(dataKaryawan, dto);
                    }
                }

                Optional<EmployeeWfoMaster> wfhToWfoList = employeeWfoMasterService.findByNikAndDefaultFloor(
                        dto.getNik(), dto.getDefaultFloor());

                if(wfhToWfoList.isPresent()) {
                    wfhToWfoList.get().setActive(true);
                    employeeWfoMasterService.save(wfhToWfoList.get());
                } else {
                    EmployeeWfoMaster employeeWfoMaster = new EmployeeWfoMaster();
                    employeeWfoMaster.setNik(dto.getNik());
                    employeeWfoMaster.setEmail(dto.getEmail());
                    employeeWfoMaster.setDivisionDescription(dto.getDivisionDescription());
                    employeeWfoMaster.setDefaultFloor(dto.getDefaultFloor());
                    employeeWfoMaster.setCreatedBy("system");
                    employeeWfoMaster.setDateCreated(DateUtils.toOffsetDateTime(date));
                    employeeWfoMaster.setActive(false);
                    employeeWfoMasterService.save(employeeWfoMaster);
                }

                map.put(dto.getBuildingId(), buildingTemporaryService.getAllByBuildingId(dto.getBuildingId()));
            }

            rabbitMqSender.send("amq.topic", "EmployeeFaceRegister", map);
            log.info("== send to mq employee face register");
        }catch (Exception e) {
            log.info("== {} ==", e);
        }
    }

    public void splitBuildingFloor(String[] buildingArray, BuildingTemporaryDto buildingTemporary) {
        Map floorMap = new HashMap();
        Map buildingIdMap = new HashMap();

        for(int i=1; i<=buildingArray.length; i++) {
            String[] buildingFloor = buildingArray[i - 1].split("-");

            buildingTemporary.setBuildingId(buildingFloor[0]);
            buildingTemporary.setFloor(buildingFloor[1]);

            buildingTemporaryService.saveFloor(buildingTemporary);

            buildingIdMap.put(buildingTemporary.getNik() + buildingFloor[0], buildingFloor[0]);
            floorMap.put(buildingTemporary.getNik() + buildingFloor[0], buildingFloor[1]);
        }
    }
}