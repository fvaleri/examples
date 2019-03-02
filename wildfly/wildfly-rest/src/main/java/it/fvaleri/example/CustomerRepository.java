package it.fvaleri.example;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

@ApplicationScoped
@Named
public class CustomerRepository {
    @Inject
    private EntityManager em;

    @Inject
    private UserTransaction userTransaction;

    public List<Customer> findAll() {
        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<Customer> query = criteriaBuilder.createQuery(Customer.class);
        query.select(query.from(Customer.class));
        return em.createQuery(query).getResultList();
    }

    public Customer findById(Long id) {
        return em.find(Customer.class, id);
    }

    public Customer save(Customer customer) {
        try {
            try {
                userTransaction.begin();
                return em.merge(customer);
            } finally {
                userTransaction.commit();
            }
        } catch (Exception e) {
            try {
                userTransaction.rollback();
            } catch (SystemException se) {
                throw new RuntimeException(se);
            }
            throw new RuntimeException(e);
        }
    }

    public void delete(Customer customer) {
        try {
            try {
                userTransaction.begin();
                if (!em.contains(customer)) {
                    customer = em.merge(customer);
                }
                em.remove(customer);
            } finally {
                userTransaction.commit();
            }
        } catch (Exception e) {
            try {
                userTransaction.rollback();
            } catch (SystemException se) {
                throw new RuntimeException(se);
            }
            throw new RuntimeException(e);
        }
    }
}

