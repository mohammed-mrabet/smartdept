package ma.ump.smartdept.academic;

import java.util.Map;

/**
 * Calcul de la note d'un module et de sa validation (règles LMD simplifiées) :
 * - note = 30 % CC + 50 % examen + 20 % TP (sans TP : 40 % CC + 60 % examen ; sans CC : 70 % examen + 30 % TP) ;
 * - le rattrapage remplace l'examen s'il est meilleur ;
 * - module validé à 10/20, validé par compensation si la moyenne du semestre atteint 10 et la note 5 (note éliminatoire).
 */
public final class GradeCalculator {

    public static final double PASS = 10.0;
    public static final double ELIMINATORY = 5.0;

    private GradeCalculator() {
    }

    public static Double average(Map<Assessment, Double> g) {
        Double cc = g.get(Assessment.CC);
        Double tp = g.get(Assessment.TP);
        Double exam = g.get(Assessment.EXAM);
        Double ratt = g.get(Assessment.RATTRAPAGE);
        if (ratt != null && (exam == null || ratt > exam)) exam = ratt;
        if (exam == null) return null;
        double avg;
        if (cc != null && tp != null) avg = 0.3 * cc + 0.5 * exam + 0.2 * tp;
        else if (cc != null) avg = 0.4 * cc + 0.6 * exam;
        else if (tp != null) avg = 0.7 * exam + 0.3 * tp;
        else avg = exam;
        return round(avg);
    }

    /** VALIDE, COMPENSE, AJOURNE ou INCOMPLET (examen pas encore noté). */
    public static String status(Double average, Double semesterAverage) {
        if (average == null) return "INCOMPLET";
        if (average >= PASS) return "VALIDE";
        if (semesterAverage != null && semesterAverage >= PASS && average >= ELIMINATORY) return "COMPENSE";
        return "AJOURNE";
    }

    public static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
