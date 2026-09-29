package mx.fondo.service;

import mx.fondo.model.Vocal;
import mx.fondo.repository.FondoRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Spring Security pregunta aquí por el usuario al hacer login: se busca en la tabla grupo. */
@Service
public class VocalService implements UserDetailsService {

    private final FondoRepository repository;

    public VocalService(FondoRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String usuario) {
        Vocal vocal = repository.vocalPorUsuario(usuario)
                .orElseThrow(() -> new UsernameNotFoundException(usuario));
        return User.withUsername(vocal.usuario())
                .password(vocal.password())
                .roles("VOCAL")
                .build();
    }
}
