package ms.hispam.budget.service;

import ms.hispam.budget.dto.RangeBuDTO;
import ms.hispam.budget.dto.RangeBuDetail;
import ms.hispam.budget.dto.RangeBuDetailDTO;
import ms.hispam.budget.entity.mysql.DaysVacationOfTime;
import ms.hispam.budget.repository.mysql.DaysVacationOfTimeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MexicoService {

    private final DaysVacationOfTimeRepository daysVacationOfTimeRepository;
    private final DaysVacationOfTimeService daysVacationOfTimeService;
    @Autowired
    public MexicoService(DaysVacationOfTimeRepository daysVacationOfTimeRepository, DaysVacationOfTimeService daysVacationOfTimeService) {
        this.daysVacationOfTimeRepository = daysVacationOfTimeRepository;
        this.daysVacationOfTimeService = daysVacationOfTimeService;
    }

    @Cacheable("daysVacationCache")
    public List<RangeBuDetailDTO> getAllDaysVacation(List<RangeBuDetailDTO> rangeBu, Integer idBu) {
        //List<DaysVacationOfTime> originalList = daysVacationOfTimeRepository.findAll();
        //daysVacationOfTimeService.replaceValues(originalList, rangeBu);
        return rangeBu;
    }

}