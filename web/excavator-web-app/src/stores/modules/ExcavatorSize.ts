import { create } from "zustand";

interface ExcavatorSizeState {
    models: Record<string, Record<string, number>>;
    update: (id: string, params: Record<string, number>) => void;
}

export const useExcavatorSizeStore = create<ExcavatorSizeState>((set) => ({
    models: {
        model_301: {
            Lb  : 1.7654,
            Ls  : 1.0325,
            L2  : 0.1957,
            L3  : 0.7727,
            L4  : 0.8501,
            L5  : 0.2142,
            L6  : 1.0325,
            L7  : 0.2030,
            L9  : 0.0,
            L10 : 0.0,
            L11 : 0.0,
            L12 : 0.0,
            L13 : 0.0,
            L14 : 0.0,
            H1  : 0,
            W   : 0,
            H2  : 0
        },
        model_320: {
            Lb: 5.6719,
            Ls: 2.9192,
            L2: 0.6330,
            L3: 2.2575,
            L4: 2.5152,
            L5: 0.7590,
            L6: 2.9192,
            L7: 0.4086,
            L9: 0.5947,
            L10 : 0.4476,
            L11 : 0.0,
            L12 : 0.0,
            L13 : 0.0,
            L14 : 0.0,
            H1: 0.0,
            W : 0.0,
            H2: 0.0
        },
        model_336: {
            Lb  : 6.5091,
            Ls  : 2.7950,
            L2  : 0.7142,
            L3  : 2.3443,
            L4  : 2.2931,
            L5  : 0.7612,
            L6  : 2.7950,
            L7  : 0.5026,
            L9  : 0.6652,
            L10 : 0.4940,
            L11 : 0.0,
            L12 : 0.0,
            L13 : 0.0,
            L14 : 0.0,
            H1  : 0.0,  
            W   : 0.0,
            H2  : 0.0    
        }
    },
    update: (id: string, params: Record<string, number>) => set((state) => ({
       models: {
        ...state.models,
        [id]: {
            ...state.models[id],
            ...params,
        }
       }
    }))
}))