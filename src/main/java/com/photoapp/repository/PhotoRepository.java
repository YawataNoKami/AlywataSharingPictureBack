package com.photoapp.repository;

import com.photoapp.model.Photo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PhotoRepository extends MongoRepository<Photo, String> {

    Page<Photo> findAllByOrderByTakenAtDesc(Pageable pageable);

    Optional<Photo> findBySha256Hash(String sha256Hash);
}
