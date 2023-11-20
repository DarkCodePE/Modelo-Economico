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
                .map(this::convertRangoBuPivotToRangeBuDTO)
                .collect(Collectors.toList());
    }
    public RangeBuDTO getBuWithRangos(Integer buId) {
        Optional<Bu> buOptional = buRepository.findById(buId);
        return buOptional.map(this::convertBuToRangeBuDTO).orElse(null);
    }
    private RangeBuDTO convertRangoBuPivotToRangeBuDTO(RangoBuPivot rangoBuPivot) {
        List<RangoBuPivot> rangeBuDetails = pivotBuRangeRepository.findByBu_Id(rangoBuPivot.getBu().getId());
        List<RangeBuDetailDTO> rangeBuDet = rangeBuDetails.stream()
                .flatMap(pivotBuRange -> convertPivotBuRangeToRangeBuDetail(pivotBuRange).stream())
                .collect(Collectors.toList());
        return RangeBuDTO.builder()
                .idBu(rangoBuPivot.getBu().getId())
                .name(rangoBuPivot.getName())
                .rangeBuDetails(rangeBuDet)
                .build();
    }

    private RangeBuDetailDTO convertRangeBuDetail(RangeBuDetailDTO rangeBuDetail) {
        return RangeBuDetailDTO.builder()
                .id(rangeBuDetail.getId())
                .idPivot(rangeBuDetail.getIdPivot())
                .range(rangeBuDetail.getRange())
                .value(rangeBuDetail.getValue())
                .build();
    }
    private RangeBuDTO convertBuToRangeBuDTO(Bu bu) {
        List<RangoBuPivot> pivotBuRanges = pivotBuRangeRepository.findByBu_Id(bu.getId());
        List<RangeBuDetailDTO> rangeBuDetails = pivotBuRanges.stream()
                .flatMap(pivotBuRange -> convertPivotBuRangeToRangeBuDetail(pivotBuRange).stream())
                .collect(Collectors.toList());
        RangoBuPivot pivot = pivotBuRanges.stream().findFirst().orElse(null);
        RangeBuDTO rangeBuDTO = RangeBuDTO.builder()
                .idBu(bu.getId())
                .name(pivot!= null ? pivot.getName(): " ")
                .build();
        rangeBuDTO.setRangeBuDetails(rangeBuDetails);

        return rangeBuDTO;
    }

    private List<RangeBuDetailDTO> convertPivotBuRangeToRangeBuDetail(RangoBuPivot pivotBuRange) {
        List<RangeBu> rangeBuList = rangeBuRepository.findByPivotBuRange_Id(pivotBuRange.getId());
        return rangeBuList.stream()
                .map(rangeBu -> RangeBuDetailDTO.builder()
                        .id(rangeBu.getId())
                        .idPivot(pivotBuRange.getId().intValue())
                        .range(rangeBu.getRange())
                        .value(rangeBu.getValueOfRange())
                        .build())
                .collect(Collectors.toList());
    }
}
