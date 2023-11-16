package ms.hispam.budget.service;

import ms.hispam.budget.dto.RangeBuDTO;
import ms.hispam.budget.entity.mysql.DaysVacationOfTime;
import ms.hispam.budget.repository.mysql.DaysVacationOfTimeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DaysVacationOfTimeService {
    @Autowired
    private DaysVacationOfTimeRepository repository;

}
