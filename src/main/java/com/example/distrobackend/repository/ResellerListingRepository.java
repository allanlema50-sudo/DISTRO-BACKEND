package com.example.distrobackend.repository;
import com.example.distrobackend.Domain.entity.ResellerListing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ResellerListingRepository extends JpaRepository<ResellerListing, UUID> {
    List<ResellerListing> findByDistributor_Id(UUID distributorId);
    Optional<ResellerListing> findByDistributor_IdAndManufacturerProduct_Id(UUID distributorId, UUID productId);
    Optional<ResellerListing> findByIdAndDistributor_Id(UUID id, UUID distributorId);
    @Query("select l from ResellerListing l join fetch l.manufacturerProduct p join fetch p.organization m join fetch l.distributor d where l.id=:id and l.status='ACTIVE' and p.active=true and p.publicationStatus=com.example.distrobackend.Domain.enums.ProductPublicationStatus.PUBLISHED")
    Optional<ResellerListing> findVisibleById(@Param("id") UUID id);
    @Query("""
        select l from ResellerListing l join fetch l.manufacturerProduct p join fetch p.organization m join fetch l.distributor d
        where l.status = 'ACTIVE' and p.active = true and p.publicationStatus = com.example.distrobackend.Domain.enums.ProductPublicationStatus.PUBLISHED
        and (:sellerId is null or d.id = :sellerId) and (:category = '' or lower(p.category) = lower(:category))
        and (:brand = '' or lower(p.brand) = lower(:brand))
        and (:availableOnly = false or l.quantityOnHand > l.reservedQuantity)
        and (:search = '' or lower(p.name) like concat('%',lower(:search),'%') or lower(p.sku) like concat('%',lower(:search),'%') or lower(d.name) like concat('%',lower(:search),'%'))
        """)
    Page<ResellerListing> searchCatalog(@Param("search") String search, @Param("category") String category,
        @Param("brand") String brand, @Param("sellerId") UUID sellerId,
        @Param("availableOnly") boolean availableOnly, Pageable pageable);
}
