package com.cloud_ml_app_thesis.entity;

import com.cloud_ml_app_thesis.util.ValidationUtil;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name="algorithm_configurations")
public class AlgorithmConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "algorithm_id")
    private Algorithm algorithm;

    @Column
    private String options;

    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public AlgorithmConfiguration(Algorithm algorithm){
        this.algorithm = algorithm;
        if(ValidationUtil.stringExists(algorithm.getOptions())){
            this.options = algorithm.getDefaultOptions().replaceAll("\\s+", "");
        }
    }
    public void setOptions(String options){
        if(ValidationUtil.stringExists(options)){
            this.options = options.replaceAll("\\s+", "");
        } else if(algorithm != null){
            this.options = algorithm.getOptions().replaceAll("\\s+", "");
        } else{
            this.options = null;
        }
    }
    public void setOptions(Algorithm algorithm){
        if(algorithm != null){
            this.options = algorithm.getOptions().replaceAll("\\s+", "");
        } else{
            this.options = null;
        }

        if(this.algorithm == null){
            this.algorithm = algorithm;
        }
    }
}
