import {useEffect, useRef, useState} from "react";
import {ChevronDown} from "lucide-react";
import {CUBE_FACE_COLORS} from "./cubeFaceColors";

type FaceOption = {
    value: string;
    label: string;
};

type Props = {
    value: string;
    options: readonly FaceOption[];
    onChange: (value: string) => void;
    disabled?: boolean;
};

export default function CrossFaceSelect({value, options, onChange, disabled = false}: Props) {
    const [open, setOpen] = useState(false);
    const [highlightedIndex, setHighlightedIndex] = useState(() => selectedIndex(options, value));
    const containerRef = useRef<HTMLDivElement>(null);
    const triggerRef = useRef<HTMLButtonElement>(null);
    const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
    const wasOpenRef = useRef(false);
    const selected = options.find((option) => option.value === value) ?? options[0];

    useEffect(() => {
        setHighlightedIndex(selectedIndex(options, value));
    }, [options, value]);

    useEffect(() => {
        if (!open) return;
        const closeOnOutsidePointer = (event: PointerEvent) => {
            if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
        };
        document.addEventListener("pointerdown", closeOnOutsidePointer);
        return () => document.removeEventListener("pointerdown", closeOnOutsidePointer);
    }, [open]);

    useEffect(() => {
        if (open) {
            wasOpenRef.current = true;
            optionRefs.current[highlightedIndex]?.focus();
        } else if (wasOpenRef.current) {
            wasOpenRef.current = false;
            triggerRef.current?.focus();
        }
    }, [open, highlightedIndex]);

    function choose(index: number) {
        const option = options[index];
        if (!option) return;
        setHighlightedIndex(index);
        setOpen(false);
        onChange(option.value);
    }

    function moveHighlight(direction: number) {
        if (!options.length) return;
        setHighlightedIndex((current) => (current + direction + options.length) % options.length);
    }

    function handleTriggerKeyDown(event: React.KeyboardEvent<HTMLButtonElement>) {
        if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            event.preventDefault();
            setHighlightedIndex(selectedIndex(options, value));
            setOpen(true);
        }
    }

    function handleOptionKeyDown(event: React.KeyboardEvent<HTMLButtonElement>, index: number) {
        if (event.key === "ArrowDown") {
            event.preventDefault();
            moveHighlight(1);
        } else if (event.key === "ArrowUp") {
            event.preventDefault();
            moveHighlight(-1);
        } else if (event.key === "Home") {
            event.preventDefault();
            setHighlightedIndex(0);
        } else if (event.key === "End") {
            event.preventDefault();
            setHighlightedIndex(options.length - 1);
        } else if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            choose(index);
        } else if (event.key === "Escape" || event.key === "Tab") {
            event.preventDefault();
            setOpen(false);
        }
    }

    return (
        <div className="cross-face-select" ref={containerRef}>
            <button
                ref={triggerRef}
                type="button"
                className="cross-face-trigger"
                aria-haspopup="listbox"
                aria-expanded={open}
                disabled={disabled}
                onClick={() => setOpen((current) => !current)}
                onKeyDown={handleTriggerKeyDown}
            >
                <CrossFaceSwatch face={selected?.value}/>
                <span>{selected?.label}</span>
                <ChevronDown size={14} aria-hidden="true"/>
            </button>
            {open ? (
                <div className="cross-face-menu" role="listbox" aria-label="Cross color">
                    {options.map((option, index) => (
                        <button
                            key={option.value}
                            ref={(element) => { optionRefs.current[index] = element; }}
                            type="button"
                            role="option"
                            aria-selected={option.value === value}
                            className={index === highlightedIndex ? "active" : ""}
                            onClick={() => choose(index)}
                            onKeyDown={(event) => handleOptionKeyDown(event, index)}
                            onMouseEnter={() => setHighlightedIndex(index)}
                        >
                            <CrossFaceSwatch face={option.value}/>
                            <span>{option.label}</span>
                        </button>
                    ))}
                </div>
            ) : null}
        </div>
    );
}

export function CrossFaceSwatch({face}: {face?: string}) {
    const color = face && face in CUBE_FACE_COLORS
        ? CUBE_FACE_COLORS[face as keyof typeof CUBE_FACE_COLORS]
        : undefined;
    return color ? <span className="cross-face-swatch" style={{backgroundColor: color}} aria-hidden="true" data-face={face}/> : null;
}

function selectedIndex(options: readonly FaceOption[], value: string) {
    const index = options.findIndex((option) => option.value === value);
    return index >= 0 ? index : 0;
}
