package ma.ump.smartdept.user;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;

/** Filière (ex : M2I-ISI). Les étudiants y sont rattachés, les sujets de PFE aussi. */
@Entity
@Table(name = "filieres")
public class Filiere extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 30)
    private String level;

    @Column(length = 500)
    private String description;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
