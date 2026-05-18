package org.example.capstone2_subscriptiontracker.Service;

import lombok.RequiredArgsConstructor;
import org.example.capstone2_subscriptiontracker.Api.ApiException;
import org.example.capstone2_subscriptiontracker.Model.User;
import org.example.capstone2_subscriptiontracker.Repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public void add(User user) {
        userRepository.save(user);
    }

    public void update(Integer id, User user) {
        User old = userRepository.findUserById(id);
        if (old == null) throw new ApiException("User not found");
        old.setUsername(user.getUsername());
        old.setPassword(user.getPassword());
        old.setEmail(user.getEmail());
        old.setWhatsappNumber(user.getWhatsappNumber());
        userRepository.save(old);
    }

    public void delete(Integer id) {
        User user = userRepository.findUserById(id);
        if (user == null) throw new ApiException("User not found");
        userRepository.delete(user);
    }
}
