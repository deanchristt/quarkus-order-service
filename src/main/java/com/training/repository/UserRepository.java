package com.training.repository;

import com.training.model.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<User, Long> {

    public User findByEmail(String email) {
        return find("email", email).firstResult();
    }

}
