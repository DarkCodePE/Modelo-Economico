package ms.hispam.budget.service;

import ms.hispam.budget.entity.mysql.EmployeeClassification;
import ms.hispam.budget.repository.mysql.EmployeeClassificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EmployeeClassificationService {

    private final EmployeeClassificationRepository repository;

    @Autowired
    public EmployeeClassificationService(EmployeeClassificationRepository repository) {
        this.repository = repository;
    }

}
