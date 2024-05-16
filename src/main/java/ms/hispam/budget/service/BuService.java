package ms.hispam.budget.service;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.RangeBuDTO;
import ms.hispam.budget.dto.RangeBuDetailDTO;
import ms.hispam.budget.entity.mysql.*;
import ms.hispam.budget.repository.mysql.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j(topic = "BU_SERVICE")
public class BuService {
    private final BuRepository buRepository;
    private final RangeBuPivotRepository pivotBuRangeRepository;
    private final RangeBuRepository rangeBuRepository;
    private final RangoBuPivotHistoricalRepository rangoBuPivotHistoricalRepository;
    //rangoBuPivotHistoricalDetailRepository
    private final RangoBuPivotHistoricalDetailRepository rangoBuPivotHistoricalDetailRepository;
    //rangeBuHistoricalRepository
    private final RangeBuHistoricalRepository rangeBuHistoricalRepository;
    @Autowired
    public BuService(BuRepository buRepository, RangeBuPivotRepository pivotBuRangeRepository, RangeBuRepository rangeBuRepository, RangoBuPivotHistoricalRepository rangoBuPivotHistoricalRepository, RangoBuPivotHistoricalDetailRepository rangoBuPivotHistoricalDetailRepository, RangeBuHistoricalRepository rangeBuHistoricalRepository) {
        this.buRepository = buRepository;
        this.pivotBuRangeRepository = pivotBuRangeRepository;
        this.rangeBuRepository = rangeBuRepository;
        this.rangoBuPivotHistoricalRepository = rangoBuPivotHistoricalRepository;
        this.rangoBuPivotHistoricalDetailRepository = rangoBuPivotHistoricalDetailRepository;
        this.rangeBuHistoricalRepository = rangeBuHistoricalRepository;
    }

    public List<RangeBuDTO> getAllBuWithRangos(Integer buId) {
        // Obtener los RangoBuPivot asociados con el BU dado
        List<RangoBuPivot> rangoBuPivots = pivotBuRangeRepository.findByBu_Id(buId);
        // Convertir las unidades de negocio a RangeBuDTO
        return rangoBuPivots.stream()
                .map((RangoBuPivot rangoBuPivot) -> convertRangoBuPivotToRangeBuDTO(rangoBuPivot, buId))
                .collect(Collectors.toList());
    }
    private RangeBuDTO convertRangoBuPivotToRangeBuDTO(RangoBuPivot rangoBuPivot, Integer buId) {
        List<RangoBuPivot> rangeBuDetails = pivotBuRangeRepository.findByBu_Id(rangoBuPivot.getBu().getId());
        List<RangeBuDetailDTO> rangeBuDet = rangeBuDetails.stream()
                .filter(detail -> detail.getBu().getId().equals(buId) && detail.getId().equals(rangoBuPivot.getId()))
                .flatMap(pivotBuRange -> convertPivotBuRangeToRangeBuDetail(pivotBuRange, buId).stream())
                .collect(Collectors.toList());
        ////log.debug("rangeBuDet: {}", rangeBuDet);

        return RangeBuDTO.builder()
                .idBu(rangoBuPivot.getBu().getId())
                .name(rangoBuPivot.getName())
                .rangeBuDetails(rangeBuDet)
                .build();
    }
    private List<RangeBuDetailDTO> convertPivotBuRangeToRangeBuDetail(RangoBuPivot pivotBuRange, Integer buId) {
        List<RangeBu> rangeBuList = rangeBuRepository.findByPivotBuRange_Id(pivotBuRange.getId())
                .stream()
                .filter(rangeBu -> rangeBu.getPivotBuRange().getBu().getId().equals(buId))
                         .collect(Collectors.toList());
        ////log.debug("rangeBuList: {}", rangeBuList);
        return rangeBuList.stream()
                .map(rangeBu -> RangeBuDetailDTO.builder()
                        .id(rangeBu.getId())
                        .idPivot(pivotBuRange.getId().intValue())
                        .range(rangeBu.getRange())
                        .value(rangeBu.getValueOfRange())
                        .build())
                .collect(Collectors.toList());
    }
    //save rangeBuDetail by pivotBuRangeId
    public RangeBuDetailDTO saveRangeBuDetail(RangeBuDetailDTO rangeBuDetailDTO) {
        Optional<RangoBuPivot> pivotBuRange = pivotBuRangeRepository.findById(rangeBuDetailDTO.getIdPivot().longValue());
        if (pivotBuRange.isPresent()) {
            RangeBu rangeBu = new RangeBu();
            rangeBu.setRange(rangeBuDetailDTO.getRange());
            rangeBu.setValueOfRange(rangeBuDetailDTO.getValue());
            rangeBu.setPivotBuRange(pivotBuRange.get());
            rangeBu = rangeBuRepository.save(rangeBu);
            rangeBuDetailDTO.setId(rangeBu.getId());
            return rangeBuDetailDTO;
        }
        return null;
    }
    // HISTORICAL:
    public List<RangeBuDTO> getTemporalParameterHistoricalProjections(Integer IdHistorical) {
        // Obtener el historial de proyecciones
        HistorialProjection historialProjection = new HistorialProjection();
        historialProjection.setId(IdHistorical);
        // Obtener los RangoBuPivotHistorical asociados con el historial de proyecciones
        List<RangoBuPivotHistorical> rangoBuPivotHistoricals = rangoBuPivotHistoricalRepository.findByHistorialProjection_Id(IdHistorical);
        // Convertir los RangoBuPivotHistorical a RangeBuDTO
        return rangoBuPivotHistoricals.stream()
                .map((RangoBuPivotHistorical rangoBuPivotHistorical) -> convertRangoBuPivotHistoricalToRangeBuDTO(rangoBuPivotHistorical, historialProjection))
                .collect(Collectors.toList());
    }
    public void saveRangeBuDTO(List<RangeBuDTO> rangeBuDTOList, HistorialProjection historialProjection) {
        // Aquí puedes iterar sobre la lista y guardar cada objeto RangeBuDTO
        for (RangeBuDTO rangeBuDTO : rangeBuDTOList) {
            // Convert RangeBuDTO to RangoBuPivotHistorical
            RangoBuPivotHistorical rangoBuPivotHistorical = convertToRangoBuPivotHistorical(rangeBuDTO);
            rangoBuPivotHistorical.setHistorialProjection(historialProjection);
            // Save RangoBuPivotHistorical
            rangoBuPivotHistorical = rangoBuPivotHistoricalRepository.save(rangoBuPivotHistorical);

            // Convert and save each RangeBuDetailDTO
            for (RangeBuDetailDTO detailDTO : rangeBuDTO.getRangeBuDetails()) {
                // Convert RangeBuDetailDTO to RangoBuPivotHistoricalDetail
                RangoBuPivotHistoricalDetail detail = convertToRangoBuPivotHistoricalDetail(detailDTO);

                // Set the relationship
                detail.setRangoBuPivotHistorical(rangoBuPivotHistorical);

                // Save RangoBuPivotHistoricalDetail
                rangoBuPivotHistoricalDetailRepository.save(detail);

                // Convert RangeBuDetailDTO to RangeBuHistorical
                RangeBuHistorical rangeBuHistorical = convertToRangeBuHistorical(detailDTO);

                // Set the relationship
                rangeBuHistorical.setRangoBuPivotHistorical(rangoBuPivotHistorical);

                // Save RangeBuHistorical
                rangeBuHistoricalRepository.save(rangeBuHistorical);
            }
        }
    }
    private RangoBuPivotHistorical convertToRangoBuPivotHistorical(RangeBuDTO rangeBuDTO) {
        RangoBuPivotHistorical rangoBuPivotHistorical = new RangoBuPivotHistorical();
        rangoBuPivotHistorical.setId(rangeBuDTO.getIdBu());
        rangoBuPivotHistorical.setName(rangeBuDTO.getName());
        rangoBuPivotHistorical.setBu(buRepository.findById(rangeBuDTO.getIdBu()).orElse(null));
        return rangoBuPivotHistorical;
    }

    private RangoBuPivotHistoricalDetail convertToRangoBuPivotHistoricalDetail(RangeBuDetailDTO rangeBuDetailDTO) {
        RangoBuPivotHistoricalDetail detail = new RangoBuPivotHistoricalDetail();
        detail.setRange(rangeBuDetailDTO.getRange());
        //detail.setIdPivot(rangeBuDetailDTO.getIdPivot());
        detail.setValue(rangeBuDetailDTO.getValue());
        // Aquí puedes establecer los demás campos de RangoBuPivotHistoricalDetail si es necesario
        return detail;
    }

    private RangeBuHistorical convertToRangeBuHistorical(RangeBuDetailDTO rangeBuDetailDTO) {
        RangeBuHistorical rangeBuHistorical = new RangeBuHistorical();
        rangeBuHistorical.setRangeOfTime(rangeBuDetailDTO.getRange());
        rangeBuHistorical.setValueOfRange(rangeBuDetailDTO.getValue());
        // Aquí puedes establecer los demás campos de RangeBuHistorical si es necesario
        return rangeBuHistorical;
    }
    //convertRangoBuPivotHistoricalToRangeBuDTO
    private RangeBuDTO convertRangoBuPivotHistoricalToRangeBuDTO(RangoBuPivotHistorical rangoBuPivotHistorical, HistorialProjection historialProjection) {
        List<RangoBuPivotHistorical> rangeBuDetails = rangoBuPivotHistoricalRepository.findByHistorialProjection_Id(historialProjection.getId());
        List<RangeBuDetailDTO> rangeBuDet = rangeBuDetails.stream()
                .filter(detail -> detail.getHistorialProjection().getId().equals(historialProjection.getId()) && detail.getId().equals(rangoBuPivotHistorical.getId()))
                .flatMap(pivotBuRange -> rangoBuPivotHistoricalDetailRepository.findByRangoBuPivotHistorical_Id(pivotBuRange.getId()).stream())
                .map(this::convertRangoBuPivotHistoricalDetailToRangeBuDetailDTO)
                .collect(Collectors.toList());
        ////log.debug("rangeBuDet: {}", rangeBuDet);

        return RangeBuDTO.builder()
                .idBu(rangoBuPivotHistorical.getBu().getId())
                .name(rangoBuPivotHistorical.getName())
                .rangeBuDetails(rangeBuDet)
                .build();
    }
    //convertRangoBuPivotHistoricalDetailToRangeBuDetailDTO
    private RangeBuDetailDTO convertRangoBuPivotHistoricalDetailToRangeBuDetailDTO(RangoBuPivotHistoricalDetail rangoBuPivotHistoricalDetail) {
        return RangeBuDetailDTO.builder()
                .id(rangoBuPivotHistoricalDetail.getId())
                .idPivot(rangoBuPivotHistoricalDetail.getRangoBuPivotHistorical().getId())
                .range(rangoBuPivotHistoricalDetail.getRange())
                .value(rangoBuPivotHistoricalDetail.getValue())
                .build();
    }
}
