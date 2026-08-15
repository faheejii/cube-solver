import {CheckCircle2, X} from "lucide-react";
import {useEffect, useRef} from "react";

type Props = {
    message: string | null;
    onDismiss: () => void;
};

const AUTO_DISMISS_MS = 5_000;

export default function SaveToast({message, onDismiss}: Props) {
    const dismissRef = useRef(onDismiss);

    useEffect(() => {
        dismissRef.current = onDismiss;
    }, [onDismiss]);

    useEffect(() => {
        if (!message) return;
        const timeout = window.setTimeout(() => dismissRef.current(), AUTO_DISMISS_MS);
        return () => window.clearTimeout(timeout);
    }, [message]);

    if (!message) return null;

    return (
        <div className="save-toast" role="status" aria-live="polite">
            <CheckCircle2 size={18} aria-hidden="true"/>
            <span>{message}</span>
            <button type="button" onClick={onDismiss} aria-label="Dismiss notification" title="Dismiss notification">
                <X size={16} aria-hidden="true"/>
            </button>
        </div>
    );
}
