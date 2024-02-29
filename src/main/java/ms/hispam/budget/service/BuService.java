package ms.hispam.budget.service;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.RangeBuDTO;
import ms.hispam.budget.dto.RangeBuDetailDTO;
import ms.hispam.budget.entity.mysql.Bu;
import ms.hispam.budget.entity.mysql.RangeBu;
import ms.hispam.budget.entity.mysql.RangoBuPivot;
import ms.hispam.budget.repository.mysql.BuRepository;
import ms.hispam.budget.repository.mysql.RangeBuPivotRepository;
import ms.hispam.budget.repository.mysql.RangeBuRepository;
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

    @Autowired
    public BuService(BuRepository buRepository, RangeBuPivotRepository pivotBuRangeRepository, RangeBuRepository rangeBuRepository) {
        this.buRepository = buRepository;
        this.pivotBuRangeRepository = pivotBuRangeRepository;
        this.rangeBuRepository = rangeBuRepository;
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
}
