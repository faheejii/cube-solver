package database.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "solve_solutions", indexes = {
        @Index(name = "idx_solve_solutions_solve_id", columnList = "solve_id"),
        @Index(name = "idx_solve_solutions_mode", columnList = "mode")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_solve_solutions_solve_mode", columnNames = {"solve_id", "mode"})
})
public class SolveSolutionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solve_id", nullable = false)
    private SolveEntity solve;

    @Column(nullable = false)
    private String mode;

    @Column(nullable = false)
    private String status;

    @Column(name = "cross_face_requested", nullable = false)
    private String crossFaceRequested;

    @Column(name = "cross_face_chosen", nullable = false)
    private String crossFaceChosen;

    @Column(nullable = false)
    private String solution;

    @Column(name = "normalized_solution")
    private String normalizedSolution;

    @Column(name = "f2l_setup_case_count", nullable = false)
    private int f2lSetupCaseCount;

    @Column(name = "f2l_insert_case_count", nullable = false)
    private int f2lInsertCaseCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "f2l_trace_json", nullable = false, columnDefinition = "jsonb")
    private String f2lTraceJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comparison_json", columnDefinition = "jsonb")
    private String comparisonJson;

    @Column(name = "cross_algorithm", nullable = false)
    private String crossAlgorithm;
    @Column(name = "cross_moves", nullable = false)
    private int crossMoves;
    @Column(name = "cross_solved", nullable = false)
    private boolean crossSolved;
    @Column(name = "cross_status", nullable = false)
    private String crossStatus;

    @Column(name = "f2l_algorithm", nullable = false)
    private String f2lAlgorithm;
    @Column(name = "f2l_moves", nullable = false)
    private int f2lMoves;
    @Column(name = "f2l_solved", nullable = false)
    private boolean f2lSolved;
    @Column(name = "f2l_status", nullable = false)
    private String f2lStatus;

    @Column(name = "oll_algorithm", nullable = false)
    private String ollAlgorithm;
    @Column(name = "oll_moves", nullable = false)
    private int ollMoves;
    @Column(name = "oll_solved", nullable = false)
    private boolean ollSolved;
    @Column(name = "oll_status", nullable = false)
    private String ollStatus;

    @Column(name = "pll_algorithm", nullable = false)
    private String pllAlgorithm;
    @Column(name = "pll_moves", nullable = false)
    private int pllMoves;
    @Column(name = "pll_solved", nullable = false)
    private boolean pllSolved;
    @Column(name = "pll_status", nullable = false)
    private String pllStatus;

    @Column(name = "solved_f2l_slots", nullable = false)
    private String solvedF2LSlots;
    @Column(name = "total_moves", nullable = false)
    private int totalMoves;
    @Column(name = "solve_elapsed_ms", nullable = false)
    private double solveElapsedMs;
    @Column(name = "fully_solved", nullable = false)
    private boolean fullySolved;
    @Column(name = "solver_version")
    private String solverVersion;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SolveSolutionEntity() {
    }

    public Long getId() { return id; }
    public SolveEntity getSolve() { return solve; }
    public void setSolve(SolveEntity solve) { this.solve = solve; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCrossFaceRequested() { return crossFaceRequested; }
    public void setCrossFaceRequested(String value) { crossFaceRequested = value; }
    public String getCrossFaceChosen() { return crossFaceChosen; }
    public void setCrossFaceChosen(String value) { crossFaceChosen = value; }
    public String getSolution() { return solution; }
    public void setSolution(String value) { solution = value; }
    public String getNormalizedSolution() { return normalizedSolution; }
    public void setNormalizedSolution(String value) { normalizedSolution = value; }
    public int getF2lSetupCaseCount() { return f2lSetupCaseCount; }
    public void setF2lSetupCaseCount(int value) { f2lSetupCaseCount = value; }
    public int getF2lInsertCaseCount() { return f2lInsertCaseCount; }
    public void setF2lInsertCaseCount(int value) { f2lInsertCaseCount = value; }
    public String getF2lTraceJson() { return f2lTraceJson; }
    public void setF2lTraceJson(String value) { f2lTraceJson = value; }
    public String getComparisonJson() { return comparisonJson; }
    public void setComparisonJson(String value) { comparisonJson = value; }
    public String getCrossAlgorithm() { return crossAlgorithm; }
    public void setCrossAlgorithm(String value) { crossAlgorithm = value; }
    public int getCrossMoves() { return crossMoves; }
    public void setCrossMoves(int value) { crossMoves = value; }
    public boolean isCrossSolved() { return crossSolved; }
    public void setCrossSolved(boolean value) { crossSolved = value; }
    public String getCrossStatus() { return crossStatus; }
    public void setCrossStatus(String value) { crossStatus = value; }
    public String getF2lAlgorithm() { return f2lAlgorithm; }
    public void setF2lAlgorithm(String value) { f2lAlgorithm = value; }
    public int getF2lMoves() { return f2lMoves; }
    public void setF2lMoves(int value) { f2lMoves = value; }
    public boolean isF2lSolved() { return f2lSolved; }
    public void setF2lSolved(boolean value) { f2lSolved = value; }
    public String getF2lStatus() { return f2lStatus; }
    public void setF2lStatus(String value) { f2lStatus = value; }
    public String getOllAlgorithm() { return ollAlgorithm; }
    public void setOllAlgorithm(String value) { ollAlgorithm = value; }
    public int getOllMoves() { return ollMoves; }
    public void setOllMoves(int value) { ollMoves = value; }
    public boolean isOllSolved() { return ollSolved; }
    public void setOllSolved(boolean value) { ollSolved = value; }
    public String getOllStatus() { return ollStatus; }
    public void setOllStatus(String value) { ollStatus = value; }
    public String getPllAlgorithm() { return pllAlgorithm; }
    public void setPllAlgorithm(String value) { pllAlgorithm = value; }
    public int getPllMoves() { return pllMoves; }
    public void setPllMoves(int value) { pllMoves = value; }
    public boolean isPllSolved() { return pllSolved; }
    public void setPllSolved(boolean value) { pllSolved = value; }
    public String getPllStatus() { return pllStatus; }
    public void setPllStatus(String value) { pllStatus = value; }
    public String getSolvedF2LSlots() { return solvedF2LSlots; }
    public void setSolvedF2LSlots(String value) { solvedF2LSlots = value; }
    public int getTotalMoves() { return totalMoves; }
    public void setTotalMoves(int value) { totalMoves = value; }
    public double getSolveElapsedMs() { return solveElapsedMs; }
    public void setSolveElapsedMs(double value) { solveElapsedMs = value; }
    public boolean isFullySolved() { return fullySolved; }
    public void setFullySolved(boolean value) { fullySolved = value; }
    public String getSolverVersion() { return solverVersion; }
    public void setSolverVersion(String value) { solverVersion = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void touchUpdatedAt() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
