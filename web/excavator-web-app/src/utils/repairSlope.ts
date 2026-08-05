type CoordinateValue = number | string;

export type RepairSlopePoint = {
  longitude: CoordinateValue;
  latitude: CoordinateValue;
  height: CoordinateValue;
};

export type RepairSlopeMeasurements = {
  abHeightDifference: number;
  verticalHeight: number;
  horizontalDistance: number;
  slopeRatio: number | null;
  slopeAngle: number;
};

const WGS84_SEMI_MAJOR_AXIS = 6_378_137;
const WGS84_ECCENTRICITY_SQUARED = 6.69437999014e-3;
const DEGREES_TO_RADIANS = Math.PI / 180;
const MIN_DISTANCE = 1e-6;

const toFiniteNumber = (value: CoordinateValue) => {
  if (typeof value === "string" && value.trim() === "") return null;

  const result = Number(value);
  return Number.isFinite(result) ? result : null;
};

const normalizePoint = (point: RepairSlopePoint) => {
  const longitude = toFiniteNumber(point.longitude);
  const latitude = toFiniteNumber(point.latitude);
  const height = toFiniteNumber(point.height);

  if (
    longitude === null ||
    latitude === null ||
    height === null ||
    longitude < -180 ||
    longitude > 180 ||
    latitude < -90 ||
    latitude > 90 ||
    (longitude === 0 && latitude === 0 && height === 0)
  ) {
    return null;
  }

  return { longitude, latitude, height };
};

const createLocalPointConverter = (
  origin: NonNullable<ReturnType<typeof normalizePoint>>,
) => {
  const latitudeAtOrigin = origin.latitude * DEGREES_TO_RADIANS;
  const sinLatitude = Math.sin(latitudeAtOrigin);
  const latitudeScale = Math.sqrt(
    1 - WGS84_ECCENTRICITY_SQUARED * sinLatitude * sinLatitude,
  );
  const primeVerticalRadius = WGS84_SEMI_MAJOR_AXIS / latitudeScale;
  const meridianRadius =
    (WGS84_SEMI_MAJOR_AXIS * (1 - WGS84_ECCENTRICITY_SQUARED)) /
    latitudeScale ** 3;

  return (point: NonNullable<ReturnType<typeof normalizePoint>>) => ({
    x:
      (point.longitude - origin.longitude) *
      DEGREES_TO_RADIANS *
      primeVerticalRadius *
      Math.cos(latitudeAtOrigin),
    y:
      (point.latitude - origin.latitude) *
      DEGREES_TO_RADIANS *
      meridianRadius,
    z: point.height,
  });
};

/** Calculates the 3D distance in metres between two RTK points. */
export const calculateRtkPointDistance = (
  pointA: RepairSlopePoint,
  pointB: RepairSlopePoint,
) => {
  const a = normalizePoint(pointA);
  const b = normalizePoint(pointB);

  if (!a || !b) return null;

  const localB = createLocalPointConverter(a)(b);
  return Math.hypot(localB.x, localB.y, b.height - a.height);
};

/**
 * Calculates the cross-section of a slope defined by baseline AB and point C.
 * Longitude/latitude are converted to a local WGS84 tangent plane based at A.
 */
export const calculateRepairSlopeMeasurements = (
  pointA: RepairSlopePoint,
  pointB: RepairSlopePoint,
  pointC: RepairSlopePoint,
): RepairSlopeMeasurements | null => {
  const a = normalizePoint(pointA);
  const b = normalizePoint(pointB);
  const c = normalizePoint(pointC);

  if (!a || !b || !c) return null;

  const toLocalPoint = createLocalPointConverter(a);
  const localB = toLocalPoint(b);
  const localC = toLocalPoint(c);
  const abSquared = localB.x ** 2 + localB.y ** 2;

  if (abSquared < MIN_DISTANCE ** 2) return null;

  // D is C's plan-view perpendicular projection onto the infinite AB line.
  const projectionRatio =
    (localC.x * localB.x + localC.y * localB.y) / abSquared;
  const projectedX = projectionRatio * localB.x;
  const projectedY = projectionRatio * localB.y;
  const projectedHeight =
    a.height + projectionRatio * (b.height - a.height);

  const horizontalDistance = Math.hypot(
    localC.x - projectedX,
    localC.y - projectedY,
  );
  const verticalHeight = Math.abs(localC.z - projectedHeight);

  return {
    abHeightDifference: Math.abs(b.height - a.height),
    verticalHeight,
    horizontalDistance,
    slopeRatio:
      verticalHeight >= MIN_DISTANCE
        ? horizontalDistance / verticalHeight
        : null,
    slopeAngle:
      Math.atan2(verticalHeight, horizontalDistance) /
      DEGREES_TO_RADIANS,
  };
};

export const formatMeasurement = (value: number, decimalPlaces: number) =>
  value
    .toFixed(decimalPlaces)
    .replace(/\.0+$/, "")
    .replace(/(\.\d*?)0+$/, "$1");
