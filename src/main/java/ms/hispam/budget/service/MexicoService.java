package ms.hispam.budget.service;

import ms.hispam.budget.entity.mysql.DaysVacationOfTime;
import ms.hispam.budget.repository.mysql.DaysVacationOfTimeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MexicoService {

    private final DaysVacationOfTimeRepository daysVacationOfTimeRepository;

    @Autowired
    public MexicoService(DaysVacationOfTimeRepository daysVacationOfTimeRepository) {
        this.daysVacationOfTimeRepository = daysVacationOfTimeRepository;
    }

    @Cacheable("daysVacationCache")
    public List<DaysVacationOfTime> getAllDaysVacation() {
        return daysVacationOfTimeRepository.findAll();
    }
}