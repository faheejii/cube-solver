import type {TimerPenalty} from "./hooks/useTimer";

const OPTIONS: Array<{value: TimerPenalty; label: string}> = [
    {value: "none", label: "No penalty"},
    {value: "+2", label: "+2"},
    {value: "dnf", label: "DNF"},
];

type Props = {
    value: TimerPenalty;
    saving: boolean;
    error: string | null;
    onChange: (penalty: TimerPenalty) => void;
    label?: string;
};

export default function SolvePenaltyControl({
    value,
    saving,
    error,
    onChange,
    label = "Penalty for most recent solve",
}: Props) {
    return (
        <div className="solve-penalty-control">
            <div className="solve-penalty-options" role="group" aria-label={label} aria-busy={saving}>
                {OPTIONS.map((option) => (
                    <button
                        type="button"
                        key={option.value}
                        aria-pressed={value === option.value}
                        disabled={saving}
                        onClick={() => onChange(option.value)}
                    >
                        {option.label}
                    </button>
                ))}
            </div>
            {saving ? <span className="solve-penalty-status" role="status">Saving penalty…</span> : null}
            {error ? <span className="solve-penalty-error" role="alert">{error}</span> : null}
        </div>
    );
}
