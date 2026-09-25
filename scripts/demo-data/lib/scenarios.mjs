// The demo cases. EVERYTHING here is invented: the names, addresses, employers and numbers do not belong to
// anyone. Report IDs start with DEMO- and the documents are stamped SPECIMEN.
//
// The Aadhaar numbers are built with the real checksum so they pass the format check, from patterned
// prefixes that no person is known to hold. The PAN numbers (ZZZP?0001?) and UANs (1000000000nn) are
// patterned as well. The phone numbers use the well-known "98765 4321x" example range.
import { fakeAadhaar } from './verhoeff.mjs'

const PRESETS = {
  COMPLETED: ['Completed', 'All Requested Verifications Completed'],
  DISCREPANCY: ['Discrepancy', 'Discrepancy Found in Verification'],
  UNABLE: ['Unable to Verify', 'Unable to Complete Verification'],
  CLOSED: ['Closed', 'Verification Closed / Insufficient Data'],
}

const CLIENTS = [
  {
    key: 'sunrise',
    name: 'Sunrise Demo Technologies Pvt Ltd',
    displayName: 'Sunrise Demo Technologies\nPrivate Limited',
    defaultCheckTypes: ['AADHAAR', 'PAN', 'COURT', 'EMPLOYMENT', 'EDUCATION'],
  },
  {
    key: 'harbor',
    name: 'Harbor Logistics Demo LLP',
    displayName: 'Harbor Logistics Demo LLP',
    defaultCheckTypes: ['AADHAAR', 'PAN', 'ADDRESS', 'COURT'],
  },
]

const asha = {
  slug: 'asha-rao', fullName: 'Asha Rao', parentType: 'FATHER', parentName: 'Ravi Rao', dob: '1994-05-17', dobText: '17/05/1994',
  gender: 'Female', phone: '9876543210', street: '14, 3rd Cross, Demo Layout', city: 'Bengaluru', state: 'Karnataka', pin: '560038',
  employeeId: 'SDT-0142', aadhaar: fakeAadhaar('23456789012'), pan: 'ZZZPR0001Z', uan: '100000000001',
  employer: 'Lakeview Demo Systems Pvt Ltd', designation: 'Senior Analyst', joined: '01 Jun 2019', left: '30 Apr 2023',
  degree: 'B.Com', specialization: 'Accounting and Finance', yearOfPassing: 2015, grade: '71%', roll: 'DEMO-15-0417',
  shirt: '#7c3aed', backdrop: '#e7dcf7',
}

const rohan = {
  slug: 'rohan-deshmukh', fullName: 'Rohan Deshmukh', parentType: 'FATHER', parentName: 'Sanjay Deshmukh', dob: '1990-11-03', dobText: '03/11/1990',
  gender: 'Male', phone: '9876543211', street: 'Flat 302, Sample Heights, Demo Nagar', city: 'Pune', state: 'Maharashtra', pin: '411045',
  employeeId: 'SDT-0207', aadhaar: fakeAadhaar('34567890123'), pan: 'ZZZPD0002Z', uan: '100000000002',
  employer: 'Bluebird Demo Foods Ltd', designation: 'Operations Executive', joined: '15 Jan 2017', left: '31 Mar 2021',
  degree: 'B.E.', specialization: 'Mechanical Engineering', yearOfPassing: 2012, grade: '64%', roll: 'DEMO-12-2291',
  shirt: '#0f766e', backdrop: '#d5efe9', skin: '#c68e63',
}

const meera = {
  slug: 'meera-iyer', fullName: 'Meera Iyer', parentType: 'GUARDIAN', parentName: 'Lakshmi Iyer', dob: '1998-02-25', dobText: '25/02/1998',
  gender: 'Female', phone: '9876543212', street: '7, Sample Street, Demo Colony', city: 'Chennai', state: 'Tamil Nadu', pin: '600042',
  employeeId: 'HLD-0031', aadhaar: fakeAadhaar('45678901234'), pan: 'ZZZPI0003Z', uan: '100000000003',
  employer: 'Demo Retail Co', designation: 'Executive', joined: '01 Jul 2021', left: '30 Jun 2024',
  degree: 'B.Sc.', specialization: 'Statistics', yearOfPassing: 2019, grade: '8.1 CGPA', roll: 'DEMO-19-0088',
  shirt: '#be123c', backdrop: '#fbdde3', skin: '#e0b48f',
}

const anitha = {
  slug: 'anitha-gowda', fullName: 'Anitha Gowda', parentType: 'FATHER', parentName: 'Mahesh Gowda', dob: '1992-08-09', dobText: '09/08/1992',
  gender: 'Female', phone: '9876543213', street: '221, 5th Main, Sample Extension', city: 'Mysuru', state: 'Karnataka', pin: '570017',
  employeeId: 'HLD-0118', aadhaar: fakeAadhaar('56789012345'), pan: 'ZZZPG0004Z', uan: '100000000004',
  employer: 'Greenfield Demo Exports Pvt Ltd', designation: 'Team Lead - Accounts', joined: '10 Sep 2014', left: '15 Feb 2022',
  degree: 'M.Com', specialization: 'Taxation', yearOfPassing: 2016, grade: 'First class', roll: 'DEMO-16-1310', cibil: 764,
  shirt: '#b45309', backdrop: '#f7e6cf', skin: '#d3a074',
}

const farhan = {
  slug: 'farhan-qureshi', fullName: 'Farhan Qureshi', parentType: 'FATHER', parentName: 'Imran Qureshi', dob: '1996-12-30', dobText: '30/12/1996',
  gender: 'Male', phone: '9876543214', street: '9, Sample Lane, Demo Bagh', city: 'Hyderabad', state: 'Telangana', pin: '500034',
  employeeId: 'SDT-0333', aadhaar: fakeAadhaar('67890123456'), pan: 'ZZZPQ0005Z', uan: '100000000005',
  employer: 'Demo Cabs Pvt Ltd', designation: 'Support Associate', joined: '01 Feb 2020', left: '31 Jan 2023',
  degree: 'B.A.', specialization: 'Economics', yearOfPassing: 2017, grade: '58%', roll: 'DEMO-17-0777',
  shirt: '#1d4ed8', backdrop: '#dbe6fb', skin: '#c9955f',
}

const MASTER = { requested: '2026-05-19', completed: '2026-06-10' }

const identity = (p) => ({ aadhaar_number: p.aadhaar })

/** One check of a scenario. `fields` holds only what is typed by hand; the candidate's data fills the rest. */
const check = (type, status, extra = {}) => ({ type, status, ...extra })

export const SCENARIOS = [
  {
    reportId: 'DEMO-2026-0001',
    note: 'Everything verified. 5 checks in 4 icon groups: the remarks get their own page 2.',
    client: 'sunrise',
    person: asha,
    period: { show: true, start: '2026-05-19', end: '2026-06-10' },
    overview: { preset: 'COMPLETED' },
    remarks: {
      analystRemarks: 'All requested verifications were completed successfully. The identity documents were matched against the records provided by the candidate.\n<strong>No adverse findings</strong> were noted in any check.',
      finalRecommendation: '<strong>Recommended</strong> for onboarding. No further verification is required.',
    },
    settings: { layoutCards: 4, dateFormat: 'NUMERIC', watermarkEnabled: false },
    checks: [
      check('AADHAAR', 'VERIFIED', { fields: identity(asha), dates: MASTER, docs: [{ design: 'aadhaar', label: 'Original Document' }] }),
      check('PAN', 'VERIFIED', { fields: { pan_number: asha.pan }, docs: [{ design: 'pan' }] }),
      check('COURT', 'VERIFIED', {
        fields: { search_period: 'Last 7 years' },
        details: [{ label: 'Court Type', value: 'Civil and Criminal' }],
        remarks: 'No criminal or civil record was found against the candidate.',
        attestation: true,
        docs: [{ design: 'court', variant: 'clear' }],
      }),
      check('EMPLOYMENT', 'VERIFIED', {
        fields: {
          company: asha.employer, designation: asha.designation, date_of_joining: '2019-06-01', date_of_leaving: '2023-04-30',
          reason_for_leaving: 'Better opportunity', eligible_for_rehire: 'Yes', verifier_name: 'Kavya N', verifier_designation: 'HR Manager',
          verifier_contact: 'hr@lakeview-demo.example',
        },
        remarks: 'Employment details were confirmed with the HR department by e-mail.',
        docs: [{ design: 'employment-pdf', label: 'Experience Letter (PDF)' }],
        free: [{ kind: 'TEXT', text: 'Confirmed by e-mail on 05-Jun-2026. The employer reported no disciplinary action on record.' }],
      }),
      check('EDUCATION', 'VERIFIED', {
        fields: {
          institution: 'Sample College of Commerce', university_board: 'Demo State University', degree: asha.degree,
          specialization: asha.specialization, year_of_passing: '2015', roll_number: asha.roll, grade: asha.grade, verifier: 'Registrar Office',
        },
        docs: [{ design: 'degree' }],
      }),
    ],
    pdf: true,
  },
  {
    reportId: 'DEMO-2026-0002',
    note: 'Discrepancies and a court hit. 6 checks in 5 icon groups: overflow summary page, text dates, watermark.',
    client: 'sunrise',
    person: rohan,
    period: { show: true, start: '2026-05-20', end: '2026-06-12' },
    overview: { preset: 'DISCREPANCY' },
    remarks: {
      analystRemarks: 'A court record was found in a name similar to the candidate\'s and needs confirmation.\nThe employment dates given by the candidate differ from the employer\'s records by <strong>two months</strong>.',
      finalRecommendation: '<strong>Hold</strong> until the client decides on the employment date difference.',
    },
    settings: { layoutCards: 4, dateFormat: 'TEXT', watermarkEnabled: true, watermarkText: 'NEXLYN VERIFIED' },
    checks: [
      check('AADHAAR', 'VERIFIED', { fields: identity(rohan), dates: { requested: '2026-05-20', completed: '2026-06-11' }, docs: [{ design: 'aadhaar' }] }),
      check('PAN', 'VERIFIED', { fields: { pan_number: rohan.pan }, docs: [{ design: 'pan' }] }),
      check('COURT', 'DISCREPANCY', {
        fields: { search_period: 'Last 7 years' },
        details: [{ label: 'Court Type', value: 'Civil' }],
        remarks: 'One civil matter was found (money recovery, 2021). The identity match is <strong>not confirmed</strong>.',
        attestation: true,
        docs: [{ design: 'court', variant: 'record' }],
      }),
      check('EMPLOYMENT', 'DISCREPANCY', {
        fields: {
          company: rohan.employer, designation: rohan.designation, date_of_joining: '2017-01-15', date_of_leaving: '2021-03-31',
          reason_for_leaving: 'Resigned', eligible_for_rehire: 'Not disclosed', verifier_name: 'Sunil P', verifier_designation: 'HR Executive',
        },
        remarks: 'The employer reports a leaving date of 31-May-2021; the candidate stated 31-Mar-2021.',
        dates: { requested: '2026-05-21', completed: '2026-06-05' },
        docs: [{ design: 'employment' }],
      }),
      check('UAN', 'VERIFIED', {
        fields: {
          uan_number: rohan.uan, establishments: `${rohan.employer}\nEarlier employer (demo)`, date_of_joining: '2017-01-15', date_of_exit: '2021-05-31',
        },
        docs: [{ design: 'uan' }],
      }),
      check('CREDIT', 'VERIFIED', {
        fields: { cibil_score: '731', report_date: '2026-06-08', defaults: 'false', summary: 'Two open accounts, none overdue.' },
        docs: [{ design: 'credit' }],
      }),
    ],
    pdf: true,
  },
  {
    reportId: 'DEMO-2026-0003',
    note: 'Short report: 2 checks in 1 group (remarks stay on page 1), 6-card layout, guardian, verification period hidden.',
    client: 'harbor',
    person: meera,
    period: { show: false, start: '2026-06-01', end: '2026-06-09' },
    overview: { preset: 'UNABLE' },
    remarks: {
      analystRemarks: 'The PAN could not be verified: the number given does not match the name. The candidate has been asked for a fresh copy.',
      finalRecommendation: 'Complete once a valid PAN is received.',
    },
    settings: { layoutCards: 6, dateFormat: 'NUMERIC', watermarkEnabled: false },
    checks: [
      check('AADHAAR', 'VERIFIED', { fields: identity(meera), dates: { requested: '2026-06-01', completed: '2026-06-09' }, docs: [{ design: 'aadhaar' }] }),
      check('PAN', 'UNABLE_TO_VERIFY', { fields: { pan_number: meera.pan }, remarks: 'The name on the PAN does not match.', docs: [{ design: 'pan' }] }),
    ],
    pdf: true,
  },
  {
    reportId: 'DEMO-2026-0004',
    note: 'Big report: 8 checks in 7 groups, Indian-script text, a small photo, larger-box and next-page documents, an image block.',
    client: 'harbor',
    person: anitha,
    period: { show: true, start: '2026-05-25', end: '2026-06-16' },
    overview: { preset: 'COMPLETED' },
    remarks: {
      analystRemarks: 'All checks are complete.\nಅಭ್ಯರ್ಥಿಯ ಎಲ್ಲಾ ದಾಖಲೆಗಳನ್ನು ಪರಿಶೀಲಿಸಲಾಗಿದೆ. (Kannada: all the candidate\'s documents were verified.)\nनमस्ते - Hindi text is included to check that Indian scripts print.',
      finalRecommendation: '<strong>Recommended.</strong>',
    },
    settings: { layoutCards: 4, dateFormat: 'NUMERIC', watermarkEnabled: false },
    checks: [
      check('AADHAAR', 'VERIFIED', { fields: identity(anitha), dates: { requested: '2026-05-25', completed: '2026-06-15' }, docs: [{ design: 'aadhaar' }] }),
      check('PAN', 'VERIFIED', { fields: { pan_number: anitha.pan }, docs: [{ design: 'pan' }] }),
      check('ADDRESS', 'VERIFIED', {
        fields: {
          address_type: 'Current', period_of_stay: '5 years', verification_mode: 'Field', respondent_name: 'Suresh (neighbour)', respondent_relationship: 'Neighbour',
        },
        remarks: 'Address confirmed by a field visit.',
        docs: [{ design: 'address', label: 'Electricity bill (phone photo)' }],
        free: [{ kind: 'TEXT', text: 'Field visit on 12-Jun-2026 at 11:15. The candidate was present.' }, { kind: 'IMAGE', design: 'sketch' }],
      }),
      check('COURT', 'VERIFIED', {
        fields: { search_period: 'Last 7 years' },
        details: [{ label: 'Court Type', value: 'Civil and Criminal' }],
        remarks: 'No record found.',
        attestation: true,
        docs: [
          { design: 'court', variant: 'clear', label: 'Search result (full page)', useLargerBox: true },
          { design: 'court', variant: 'clear', label: 'Search result (copy on its own page)', moveToNextPage: true },
        ],
      }),
      check('EMPLOYMENT', 'VERIFIED', {
        fields: {
          company: anitha.employer, designation: anitha.designation, date_of_joining: '2014-09-10', date_of_leaving: '2022-02-15',
          reason_for_leaving: 'Relocation', eligible_for_rehire: 'Yes', verifier_name: 'Deepa R', verifier_designation: 'HR Lead',
        },
        docs: [{ design: 'employment-pdf' }, { design: 'employment' }],
      }),
      check('EDUCATION', 'VERIFIED', {
        fields: {
          institution: 'Demo Institute of Commerce', university_board: 'Sample University', degree: anitha.degree, specialization: anitha.specialization,
          year_of_passing: '2016', roll_number: anitha.roll, grade: anitha.grade, verifier: 'Examination Section',
        },
        docs: [{ design: 'degree' }],
      }),
      check('UAN', 'VERIFIED', {
        fields: { uan_number: anitha.uan, establishments: anitha.employer, date_of_joining: '2014-09-10', date_of_exit: '2022-02-15' },
        docs: [{ design: 'uan' }],
      }),
      check('CREDIT', 'VERIFIED', {
        fields: { cibil_score: '764', report_date: '2026-06-14', defaults: 'false', summary: 'No overdue accounts.' },
        docs: [{ design: 'credit' }],
      }),
    ],
    pdf: true,
  },
  {
    reportId: 'DEMO-2026-0005',
    note: 'Unfinished draft: no photo, no documents, no remarks. Shows the warnings and the "acknowledge" path.',
    client: 'sunrise',
    person: farhan,
    period: { show: true, start: null, end: null },
    overview: { preset: 'CLOSED' },
    remarks: { analystRemarks: null, finalRecommendation: null },
    settings: { layoutCards: 4, dateFormat: 'NUMERIC', watermarkEnabled: false },
    noPhoto: true,
    checks: [
      check('AADHAAR', 'PENDING', { fields: identity(farhan), dates: { requested: '2026-06-15', completed: null } }),
      check('COURT', 'IN_PROGRESS', { fields: { search_period: 'Last 7 years' } }),
    ],
    pdf: true,
  },
]

export { CLIENTS, PRESETS }
