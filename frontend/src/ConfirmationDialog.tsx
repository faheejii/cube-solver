import {useEffect, useId, useRef} from "react";
import {createPortal} from "react-dom";
import {LoaderCircle} from "lucide-react";

type Props = {
    title: string;
    description: string;
    confirmLabel: string;
    cancelLabel?: string;
    pendingLabel?: string;
    tone?: "danger" | "neutral";
    pending?: boolean;
    error?: string | null;
    onCancel: () => void;
    onConfirm: () => void;
};

export default function ConfirmationDialog({
                                               title,
                                               description,
                                               confirmLabel,
                                               cancelLabel = "Cancel",
                                               pendingLabel = "Working…",
                                               tone = "neutral",
                                               pending = false,
                                               error = null,
                                               onCancel,
                                               onConfirm,
                                           }: Props) {
    const titleId = useId();
    const descriptionId = useId();
    const errorId = useId();
    const cancelRef = useRef<HTMLButtonElement>(null);
    const confirmRef = useRef<HTMLButtonElement>(null);
    const interactionRef = useRef({onCancel, pending});
    interactionRef.current = {onCancel, pending};

    useEffect(() => {
        const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        cancelRef.current?.focus();
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape" && !interactionRef.current.pending) {
                event.preventDefault();
                interactionRef.current.onCancel();
            } else if (event.key === "Tab") {
                const first = cancelRef.current;
                const last = confirmRef.current;
                if (event.shiftKey && document.activeElement === first) {
                    event.preventDefault();
                    last?.focus();
                } else if (!event.shiftKey && document.activeElement === last) {
                    event.preventDefault();
                    first?.focus();
                }
            }
        };
        window.addEventListener("keydown", onKeyDown);
        return () => {
            window.removeEventListener("keydown", onKeyDown);
            if (previouslyFocused?.isConnected) previouslyFocused.focus();
        };
    }, []);

    return createPortal((
        <div
            className="confirmation-dialog-backdrop"
            role="presentation"
            onMouseDown={(event) => {
                if (!pending && event.target === event.currentTarget) onCancel();
            }}
        >
            <section
                className="confirmation-dialog"
                role="alertdialog"
                aria-modal="true"
                aria-labelledby={titleId}
                aria-describedby={descriptionId}
                aria-busy={pending}
                onMouseDown={(event) => event.stopPropagation()}
            >
                <h2 id={titleId}>{title}</h2>
                <p id={descriptionId}>{description}</p>
                {error ? <p className="confirmation-dialog-error" id={errorId} role="alert">{error}</p> : null}
                <div className="confirmation-dialog-actions">
                    <button ref={cancelRef} type="button" onClick={onCancel} disabled={pending}>{cancelLabel}</button>
                    <button
                        ref={confirmRef}
                        className={tone === "danger" ? "danger" : "primary"}
                        type="button"
                        onClick={onConfirm}
                        disabled={pending}
                        aria-describedby={error ? errorId : undefined}
                    >
                        {pending ? <><LoaderCircle size={15}/> {pendingLabel}</> : confirmLabel}
                    </button>
                </div>
            </section>
        </div>
    ), document.body);
}
