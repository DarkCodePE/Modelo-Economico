package ms.hispam.budget.service;

import lombok.extern.slf4j.Slf4j;
import ms.hispam.budget.dto.RangeBuDTO;
import ms.hispam.budget.dto.RangeBuDetail;
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

    public List<RangeBuDTO> getAllBuWithRangos() {
        // Obtener todas las unidades de negocio
        List<Bu> allBu = buRepository.findAll();
        // Convertir las unidades de negocio a RangeBuDTO
        return allBu.stream()
                .map(this::convertBuToRangeBuDTO)
                .collect(Collectors.toList());
    }
    public RangeBuDTO getBuWithRangos(Integer buId) {
        Optional<Bu> buOptional = buRepository.findById(buId);
        return buOptional.map(this::convertBuToRangeBuDTO).orElse(null);
    }

    private RangeBuDTO convertBuToRangeBuDTO(Bu bu) {
        RangeBuDTO rangeBuDTO = RangeBuDTO.builder()
                .idBu(bu.getId())
                .build();
        List<RangoBuPivot> pivotBuRanges = pivotBuRangeRepository.findByBu_Id(bu.getId());
        List<RangeBuDetail> rangeBuDetails = pivotBuRanges.stream()
                .flatMap(pivotBuRange -> convertPivotBuRangeToRangeBuDetail(pivotBuRange).stream())
                .collect(Collectors.toList());
        rangeBuDTO.setRangeBuDetails(rangeBuDetails);

        return rangeBuDTO;
    }

    private List<RangeBuDetail> convertPivotBuRangeToRangeBuDetail(RangoBuPivot pivotBuRange) {
        List<RangeBu> rangeBuList = rangeBuRepository.findByPivotBuRange_Id(pivotBuRange.getId());
        return rangeBuList.stream()
                .map(rangeBu -> RangeBuDetail.builder()
                        .id(pivotBuRange.getId().intValue())
                        .range(rangeBu.getRange())
                        .value(rangeBu.getValueOfRange())
                        .build())
                .collect(Collectors.toList());
    }
}
