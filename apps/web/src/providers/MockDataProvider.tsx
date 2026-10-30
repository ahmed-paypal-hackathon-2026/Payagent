"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import type {
  AgreementSnapshotDTO,
  DealWorkspaceDTO,
  DeliverableEvidenceDTO,
  MandateDTO,
  OfferDTO,
  SettlementStateDTO,
  StructuredTermsDTO,
} from "@/types/contracts";
import {
  acceptAgreement as acceptAgreementInDb,
  acceptDeliverables as acceptDeliverablesInDb,
  authorizeSettlement as authorizeSettlementInDb,
  buildWorkspace,
  createIntake as createIntakeInDb,
  generateAgentOffer as generateAgentOfferInDb,
  getDealSummaries,
  initiateSettlement as initiateSettlementInDb,
  joinDeal as joinDealInDb,
  openDispute as openDisputeInDb,
  proposeAgreement as proposeAgreementInDb,
  saveMandate as saveMandateInDb,
  submitOffer as submitOfferInDb,
  uploadEvidence as uploadEvidenceInDb,
  voidSettlement as voidSettlementInDb,
} from "@/lib/mock/engine";
import { computeDemoTermsHash, sha256File } from "@/lib/mock/hash";
import { createSeedDatabase, DEFAULT_DEMO_USER_ID, MOCK_USERS } from "@/lib/mock/seed";
import type {
  DemoUser,
  IntakeInput,
  IntakeResult,
  MandateInput,
  MockDatabase,
  MockDealSummary,
} from "@/lib/mock/types";

const STORAGE_KEY = "payagent.mock.db.v1";
const USER_KEY = "payagent.mock.user.v1";

interface MockActions {
  createIntake(input: IntakeInput): Promise<IntakeResult>;
  joinDeal(dealId: string, inviteToken: string): Promise<void>;
  saveMandate(dealId: string, input: MandateInput): Promise<MandateDTO>;
  submitOffer(dealId: string, terms: StructuredTermsDTO, message: string): Promise<OfferDTO>;
  generateAgentOffer(dealId: string): Promise<OfferDTO>;
  proposeAgreement(dealId: string, terms: StructuredTermsDTO): Promise<AgreementSnapshotDTO>;
  acceptAgreement(dealId: string, version: number, hash: string): Promise<AgreementSnapshotDTO>;
  initiateSettlement(dealId: string): Promise<SettlementStateDTO>;
  authorizeSettlement(dealId: string): Promise<SettlementStateDTO>;
  voidSettlement(dealId: string, reason: string): Promise<SettlementStateDTO>;
  uploadEvidence(dealId: string, file: File): Promise<DeliverableEvidenceDTO>;
  acceptDeliverables(dealId: string, evidenceVersion: number): Promise<SettlementStateDTO>;
  openDispute(dealId: string, reason: string, resolutionRequest: string): Promise<DealWorkspaceDTO>;
  resetDemo(): void;
}

interface MockDataContextValue {
  db: MockDatabase;
  currentUser: DemoUser;
  currentUserId: string;
  users: DemoUser[];
  setCurrentUserId(userId: string): void;
  summaries: MockDealSummary[];
  getWorkspace(dealId: string): DealWorkspaceDTO | null;
  actions: MockActions;
}

const MockDataContext = createContext<MockDataContextValue | null>(null);

function cloneDatabase(db: MockDatabase): MockDatabase {
  if (typeof structuredClone === "function") return structuredClone(db);
  return JSON.parse(JSON.stringify(db)) as MockDatabase;
}

function isMockDatabase(value: unknown): value is MockDatabase {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<MockDatabase>;
  return candidate.schemaVersion === 1 && Boolean(candidate.deals) && typeof candidate.deals === "object";
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

export function MockDataProvider({ children }: { children: ReactNode }) {
  const [db, setDb] = useState<MockDatabase>(() => createSeedDatabase());
  const [currentUserId, setCurrentUserIdState] = useState(DEFAULT_DEMO_USER_ID);
  const [hydrated, setHydrated] = useState(false);
  const dbRef = useRef(db);

  useEffect(() => {
    try {
      const storedDb = window.localStorage.getItem(STORAGE_KEY);
      if (storedDb) {
        const parsed: unknown = JSON.parse(storedDb);
        if (isMockDatabase(parsed)) {
          dbRef.current = parsed;
          setDb(parsed);
        }
      }
      const storedUser = window.localStorage.getItem(USER_KEY);
      if (storedUser && MOCK_USERS.some((user) => user.id === storedUser)) {
        setCurrentUserIdState(storedUser);
      }
    } catch {
      window.localStorage.removeItem(STORAGE_KEY);
    } finally {
      setHydrated(true);
    }
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(db));
    } catch {
      // Persistence is a demo convenience; the in-memory workspace remains usable.
    }
  }, [db, hydrated]);

  const setCurrentUserId = useCallback((userId: string) => {
    if (!MOCK_USERS.some((user) => user.id === userId)) return;
    setCurrentUserIdState(userId);
    try {
      window.localStorage.setItem(USER_KEY, userId);
    } catch {
      // Ignore storage failures in private browsing.
    }
  }, []);

  const commit = useCallback(<T,>(mutator: (draft: MockDatabase) => T): T => {
    const draft = cloneDatabase(dbRef.current);
    const result = mutator(draft);
    dbRef.current = draft;
    setDb(draft);
    return result;
  }, []);

  const createIntake = useCallback(
    async (input: IntakeInput): Promise<IntakeResult> => {
      await wait(220);
      return commit((draft) => createIntakeInDb(draft, currentUserId, input));
    },
    [commit, currentUserId],
  );

  const joinDeal = useCallback(
    async (dealId: string, inviteToken: string): Promise<void> => {
      await wait(220);
      commit((draft) => joinDealInDb(draft, currentUserId, dealId, inviteToken));
    },
    [commit, currentUserId],
  );

  const saveMandate = useCallback(
    async (dealId: string, input: MandateInput): Promise<MandateDTO> => {
      await wait(220);
      return commit((draft) => saveMandateInDb(draft, currentUserId, dealId, input));
    },
    [commit, currentUserId],
  );

  const submitOffer = useCallback(
    async (dealId: string, terms: StructuredTermsDTO, message: string): Promise<OfferDTO> => {
      await wait(220);
      return commit((draft) => submitOfferInDb(draft, currentUserId, dealId, terms, message));
    },
    [commit, currentUserId],
  );

  const generateAgentOffer = useCallback(
    async (dealId: string): Promise<OfferDTO> => {
      await wait(260);
      return commit((draft) => generateAgentOfferInDb(draft, currentUserId, dealId));
    },
    [commit, currentUserId],
  );

  const proposeAgreement = useCallback(
    async (dealId: string, terms: StructuredTermsDTO): Promise<AgreementSnapshotDTO> => {
      const hash = await computeDemoTermsHash(terms);
      await wait(220);
      return commit((draft) => proposeAgreementInDb(draft, currentUserId, dealId, terms, hash));
    },
    [commit, currentUserId],
  );

  const acceptAgreement = useCallback(
    async (dealId: string, version: number, hash: string): Promise<AgreementSnapshotDTO> => {
      await wait(220);
      return commit((draft) => acceptAgreementInDb(draft, currentUserId, dealId, version, hash));
    },
    [commit, currentUserId],
  );

  const initiateSettlement = useCallback(
    async (dealId: string): Promise<SettlementStateDTO> => {
      await wait(260);
      return commit((draft) => initiateSettlementInDb(draft, currentUserId, dealId));
    },
    [commit, currentUserId],
  );

  const authorizeSettlement = useCallback(
    async (dealId: string): Promise<SettlementStateDTO> => {
      await wait(260);
      return commit((draft) => authorizeSettlementInDb(draft, currentUserId, dealId));
    },
    [commit, currentUserId],
  );

  const voidSettlement = useCallback(
    async (dealId: string, reason: string): Promise<SettlementStateDTO> => {
      await wait(220);
      return commit((draft) => voidSettlementInDb(draft, currentUserId, dealId, reason));
    },
    [commit, currentUserId],
  );

  const uploadEvidence = useCallback(
    async (dealId: string, file: File): Promise<DeliverableEvidenceDTO> => {
      const sha256Hash = await sha256File(file);
      await wait(220);
      return commit((draft) =>
        uploadEvidenceInDb(draft, currentUserId, dealId, {
          filename: file.name,
          mimeType: file.type,
          sizeBytes: file.size,
          sha256Hash,
        }),
      );
    },
    [commit, currentUserId],
  );

  const acceptDeliverables = useCallback(
    async (dealId: string, evidenceVersion: number): Promise<SettlementStateDTO> => {
      await wait(260);
      return commit((draft) => acceptDeliverablesInDb(draft, currentUserId, dealId, evidenceVersion));
    },
    [commit, currentUserId],
  );

  const openDispute = useCallback(
    async (dealId: string, reason: string, resolutionRequest: string): Promise<DealWorkspaceDTO> => {
      await wait(220);
      return commit((draft) => openDisputeInDb(draft, currentUserId, dealId, reason, resolutionRequest));
    },
    [commit, currentUserId],
  );

  const resetDemo = useCallback(() => {
    const fresh = createSeedDatabase();
    dbRef.current = fresh;
    setDb(fresh);
    setCurrentUserIdState(DEFAULT_DEMO_USER_ID);
    try {
      window.localStorage.removeItem(STORAGE_KEY);
      window.localStorage.setItem(USER_KEY, DEFAULT_DEMO_USER_ID);
    } catch {
      // Ignore storage failures.
    }
  }, []);

  const currentUser = useMemo(
    () => MOCK_USERS.find((user) => user.id === currentUserId) ?? MOCK_USERS[0],
    [currentUserId],
  );

  const summaries = useMemo(() => getDealSummaries(db, currentUserId), [db, currentUserId]);

  const getWorkspace = useCallback(
    (dealId: string) => buildWorkspace(db, dealId, currentUserId),
    [db, currentUserId],
  );

  const actions = useMemo<MockActions>(
    () => ({
      createIntake,
      joinDeal,
      saveMandate,
      submitOffer,
      generateAgentOffer,
      proposeAgreement,
      acceptAgreement,
      initiateSettlement,
      authorizeSettlement,
      voidSettlement,
      uploadEvidence,
      acceptDeliverables,
      openDispute,
      resetDemo,
    }),
    [
      acceptAgreement,
      acceptDeliverables,
      authorizeSettlement,
      createIntake,
      generateAgentOffer,
      initiateSettlement,
      joinDeal,
      openDispute,
      proposeAgreement,
      resetDemo,
      saveMandate,
      submitOffer,
      uploadEvidence,
      voidSettlement,
    ],
  );

  const value = useMemo<MockDataContextValue>(
    () => ({
      db,
      currentUser,
      currentUserId,
      users: MOCK_USERS,
      setCurrentUserId,
      summaries,
      getWorkspace,
      actions,
    }),
    [actions, currentUser, currentUserId, db, getWorkspace, setCurrentUserId, summaries],
  );

  return <MockDataContext.Provider value={value}>{children}</MockDataContext.Provider>;
}

export function useMockData(): MockDataContextValue {
  const value = useContext(MockDataContext);
  if (!value) throw new Error("useMockData must be used inside MockDataProvider.");
  return value;
}
