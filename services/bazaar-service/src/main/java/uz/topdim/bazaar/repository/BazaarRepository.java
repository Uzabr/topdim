package uz.topdim.bazaar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.bazaar.entity.Bazaar;
import java.util.List;

public interface BazaarRepository extends JpaRepository<Bazaar, Long> {
    List<Bazaar> findByActiveTrue();
    List<Bazaar> findByCityAndActiveTrue(String city);
}
