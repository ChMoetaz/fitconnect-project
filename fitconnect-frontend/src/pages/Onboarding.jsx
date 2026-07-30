// import { useState } from "react"
// import { useNavigate } from "react-router-dom"

// // ─── Data ────────────────────────────────────────────────────────────────────

// const STEPS = [
//   {
//     id: "level",
//     title: "What's your fitness level?",
//     subtitle: "Be honest — your plan adapts to where you are right now.",
//     type: "single",
//     options: [
//       { value: "beginner",     label: "Complete beginner",  icon: "🌱", desc: "Little to no training experience" },
//       { value: "some",         label: "Some experience",    icon: "🔄", desc: "Trained on and off before" },
//       { value: "intermediate", label: "Intermediate",       icon: "💪", desc: "Training consistently 6+ months" },
//       { value: "advanced",     label: "Advanced",           icon: "🏆", desc: "2+ years of structured training" },
//     ],
//   },
//   {
//     id: "goal",
//     title: "What's your main goal?",
//     subtitle: "Your AI plan is optimised around this.",
//     type: "single",
//     options: [
//       { value: "muscle",  label: "Build muscle",  icon: "💪", desc: "Increase strength and size" },
//       { value: "weight",  label: "Lose weight",   icon: "🔥", desc: "Burn fat, improve body comp" },
//       { value: "fit",     label: "Get fitter",    icon: "⚡", desc: "Improve endurance and stamina" },
//       { value: "health",  label: "Stay healthy",  icon: "❤️", desc: "General wellbeing and mobility" },
//     ],
//   },
//   {
//     id: "days",
//     title: "How many days per week?",
//     subtitle: "Pick what's realistic for your schedule — consistency beats intensity.",
//     type: "single",
//     options: [
//       { value: "2", label: "2 days",  icon: "📅", desc: "Light commitment, great start" },
//       { value: "3", label: "3 days",  icon: "📅", desc: "Recommended for beginners" },
//       { value: "4", label: "4 days",  icon: "📅", desc: "Good for faster progress" },
//       { value: "5", label: "5+ days", icon: "📅", desc: "High commitment" },
//     ],
//   },
//   {
//     id: "location",
//     title: "Where do you train?",
//     subtitle: "Your exercises will be selected for your environment.",
//     type: "single",
//     options: [
//       { value: "gym",     label: "Gym",       icon: "🏋️", desc: "Full equipment available" },
//       { value: "home",    label: "Home",      icon: "🏠", desc: "Bodyweight or minimal gear" },
//       { value: "outdoor", label: "Outdoors",  icon: "🌳", desc: "Parks, tracks, open spaces" },
//       { value: "mixed",   label: "Mixed",     icon: "🔀", desc: "Varies day to day" },
//     ],
//   },
//   {
//     id: "equipment",
//     title: "What equipment do you have?",
//     subtitle: "Select all that apply.",
//     type: "multi",
//     options: [
//       { value: "dumbbells",    label: "Dumbbells",        icon: "🏋️" },
//       { value: "barbell",      label: "Barbell & rack",   icon: "🔩" },
//       { value: "pullup",       label: "Pull-up bar",      icon: "🔝" },
//       { value: "bands",        label: "Resistance bands", icon: "〰️" },
//       { value: "kettlebell",   label: "Kettlebell",       icon: "⚫" },
//       { value: "none",         label: "No equipment",     icon: "🤲" },
//     ],
//   },
// ]

// const STEP_LABELS = ["Level", "Goal", "Schedule", "Location", "Equipment"]

// // ─── Sub-components ──────────────────────────────────────────────────────────

// function ProgressBar({ current, total }) {
//   return (
//     <div style={{ marginBottom: "32px" }}>
//       <div style={{
//         display: "flex", justifyContent: "space-between",
//         alignItems: "center", marginBottom: "10px"
//       }}>
//         {STEP_LABELS.map((label, i) => (
//           <div key={i} style={{
//             display: "flex", flexDirection: "column",
//             alignItems: "center", gap: "5px", flex: 1,
//           }}>
//             <div style={{
//               width: "28px", height: "28px", borderRadius: "50%",
//               display: "flex", alignItems: "center", justifyContent: "center",
//               fontSize: "12px", fontWeight: "600", transition: "all 0.25s",
//               background: i < current ? "#1D9E75" : i === current ? "#fff" : "transparent",
//               color: i < current ? "#fff" : i === current ? "#1D9E75" : "#9ca3af",
//               border: i === current ? "2px solid #1D9E75" : i < current ? "2px solid #1D9E75" : "2px solid #e5e7eb",
//             }}>
//               {i < current ? "✓" : i + 1}
//             </div>
//             <span style={{
//               fontSize: "10px", fontWeight: i === current ? "600" : "400",
//               color: i === current ? "#1D9E75" : i < current ? "#0F6E56" : "#9ca3af",
//             }}>
//               {label}
//             </span>
//           </div>
//         ))}
//       </div>
//       <div style={{
//         height: "3px", background: "#e5e7eb", borderRadius: "2px",
//         marginTop: "4px", overflow: "hidden",
//       }}>
//         <div style={{
//           height: "100%", background: "#1D9E75", borderRadius: "2px",
//           width: `${(current / (total - 1)) * 100}%`,
//           transition: "width 0.4s ease",
//         }} />
//       </div>
//     </div>
//   )
// }

// function OptionCard({ option, selected, multi, onToggle }) {
//   const isSelected = multi
//     ? selected.includes(option.value)
//     : selected === option.value

//   return (
//     <button
//       onClick={() => onToggle(option.value)}
//       style={{
//         display: "flex", alignItems: "flex-start", gap: "12px",
//         padding: "14px 16px", borderRadius: "12px", cursor: "pointer",
//         border: isSelected ? "2px solid #1D9E75" : "1.5px solid #e5e7eb",
//         background: isSelected ? "#f0fdf8" : "#fff",
//         textAlign: "left", transition: "all 0.15s", width: "100%",
//         outline: "none",
//       }}
//     >
//       <span style={{ fontSize: "22px", lineHeight: 1, marginTop: "1px" }}>{option.icon}</span>
//       <div style={{ flex: 1 }}>
//         <div style={{
//           fontSize: "14px", fontWeight: "600",
//           color: isSelected ? "#0F6E56" : "#111827",
//         }}>
//           {option.label}
//         </div>
//         {option.desc && (
//           <div style={{ fontSize: "12px", color: "#6b7280", marginTop: "2px" }}>
//             {option.desc}
//           </div>
//         )}
//       </div>
//       <div style={{
//         width: "18px", height: "18px", borderRadius: multi ? "4px" : "50%",
//         border: isSelected ? "none" : "1.5px solid #d1d5db",
//         background: isSelected ? "#1D9E75" : "transparent",
//         display: "flex", alignItems: "center", justifyContent: "center",
//         flexShrink: 0, marginTop: "2px", transition: "all 0.15s",
//       }}>
//         {isSelected && (
//           <svg width="10" height="8" viewBox="0 0 10 8" fill="none">
//             <path d="M1 4L3.5 6.5L9 1" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
//           </svg>
//         )}
//       </div>
//     </button>
//   )
// }

// function SummaryCard({ answers }) {
//   const labels = {
//     level:     { beginner: "Complete beginner", some: "Some experience", intermediate: "Intermediate", advanced: "Advanced" },
//     goal:      { muscle: "Build muscle", weight: "Lose weight", fit: "Get fitter", health: "Stay healthy" },
//     days:      { "2": "2 days/week", "3": "3 days/week", "4": "4 days/week", "5": "5+ days/week" },
//     location:  { gym: "Gym", home: "Home", outdoor: "Outdoors", mixed: "Mixed" },
//     equipment: answers.equipment?.join(", ") || "—",
//   }

//   const rows = [
//     { icon: "🌱", label: "Fitness level", value: labels.level[answers.level] },
//     { icon: "🎯", label: "Goal",          value: labels.goal[answers.goal] },
//     { icon: "📅", label: "Days/week",     value: labels.days[answers.days] },
//     { icon: "📍", label: "Location",      value: labels.location[answers.location] },
//     { icon: "🏋️", label: "Equipment",    value: labels.equipment },
//   ]

//   return (
//     <div style={{
//       background: "#f0fdf8", border: "1.5px solid #5DCAA5",
//       borderRadius: "14px", padding: "20px", marginBottom: "24px",
//     }}>
//       <div style={{ fontSize: "13px", fontWeight: "600", color: "#0F6E56", marginBottom: "14px" }}>
//         ✨ Your profile summary
//       </div>
//       {rows.map((r, i) => (
//         <div key={i} style={{
//           display: "flex", alignItems: "center", gap: "10px",
//           padding: "7px 0",
//           borderBottom: i < rows.length - 1 ? "1px solid #d1fae5" : "none",
//         }}>
//           <span style={{ fontSize: "16px" }}>{r.icon}</span>
//           <span style={{ fontSize: "13px", color: "#6b7280", flex: 1 }}>{r.label}</span>
//           <span style={{ fontSize: "13px", fontWeight: "600", color: "#0F6E56" }}>{r.value}</span>
//         </div>
//       ))}
//     </div>
//   )
// }

// // ─── Main Component ──────────────────────────────────────────────────────────

// export default function Onboarding() {
//   const navigate = useNavigate()
//   const [currentStep, setCurrentStep] = useState(0)
//   const [answers, setAnswers] = useState({
//     level: null, goal: null, days: null, location: null, equipment: [],
//   })
//   const [generating, setGenerating] = useState(false)

//   const step = STEPS[currentStep]
//   const isLastStep = currentStep === STEPS.length - 1

//   function handleSelect(value) {
//     if (step.type === "multi") {
//       setAnswers(prev => {
//         const current = prev.equipment
//         return {
//           ...prev,
//           equipment: current.includes(value)
//             ? current.filter(v => v !== value)
//             : [...current, value],
//         }
//       })
//     } else {
//       setAnswers(prev => ({ ...prev, [step.id]: value }))
//     }
//   }

//   function canContinue() {
//     if (step.type === "multi") return answers.equipment.length > 0
//     return answers[step.id] !== null
//   }

//   async function handleNext() {
//     if (!canContinue()) return
//     if (isLastStep) {
//       setGenerating(true)
//       // TODO: POST answers to your Spring Boot API
//       // const response = await fetch("http://localhost:8080/api/onboarding", {
//       //   method: "POST",
//       //   headers: { "Content-Type": "application/json" },
//       //   body: JSON.stringify(answers),
//       // })
//       await new Promise(r => setTimeout(r, 1800)) // simulate API call
//       setGenerating(false)
//       navigate("/plan") // redirect to training plan page
//     } else {
//       setCurrentStep(prev => prev + 1)
//     }
//   }

//   function handleBack() {
//     if (currentStep > 0) setCurrentStep(prev => prev - 1)
//   }

//   return (
//     <div style={{
//       minHeight: "100vh", display: "flex", alignItems: "center",
//       justifyContent: "center", padding: "24px",
//       background: "linear-gradient(135deg, #f0fdf8 0%, #ffffff 60%)",
//     }}>
//       <div style={{ width: "100%", maxWidth: "520px" }}>

//         {/* Header */}
//         <div style={{ textAlign: "center", marginBottom: "36px" }}>
//           <div style={{ fontSize: "26px", fontWeight: "700", color: "#0F6E56", marginBottom: "6px" }}>
//             Fit<span style={{ color: "#1D9E75" }}>Connect</span>
//           </div>
//           <div style={{ fontSize: "13px", color: "#6b7280" }}>
//             Let's build your personalised training plan
//           </div>
//         </div>

//         {/* Card */}
//         <div style={{
//           background: "#fff", borderRadius: "20px",
//           boxShadow: "0 4px 24px rgba(0,0,0,0.07)",
//           padding: "32px",
//         }}>
//           <ProgressBar current={currentStep} total={STEPS.length} />

//           {/* Step heading */}
//           <div style={{ marginBottom: "20px" }}>
//             <h1 style={{ fontSize: "20px", fontWeight: "700", color: "#111827", marginBottom: "6px" }}>
//               {step.title}
//             </h1>
//             <p style={{ fontSize: "13px", color: "#6b7280", lineHeight: "1.5" }}>
//               {step.subtitle}
//             </p>
//           </div>

//           {/* Summary on last step */}
//           {isLastStep && <SummaryCard answers={answers} />}

//           {/* Options */}
//           <div style={{
//             display: "grid",
//             gridTemplateColumns: step.type === "multi" ? "1fr 1fr" : "1fr",
//             gap: "10px", marginBottom: "24px",
//           }}>
//             {step.options.map(option => (
//               <OptionCard
//                 key={option.value}
//                 option={option}
//                 selected={step.type === "multi" ? answers.equipment : answers[step.id]}
//                 multi={step.type === "multi"}
//                 onToggle={handleSelect}
//               />
//             ))}
//           </div>

//           {/* Actions */}
//           <div style={{ display: "flex", gap: "10px" }}>
//             {currentStep > 0 && (
//               <button
//                 onClick={handleBack}
//                 style={{
//                   padding: "12px 20px", borderRadius: "10px", cursor: "pointer",
//                   border: "1.5px solid #e5e7eb", background: "#fff",
//                   fontSize: "14px", fontWeight: "500", color: "#374151",
//                   transition: "all 0.15s",
//                 }}
//               >
//                 ← Back
//               </button>
//             )}
//             <button
//               onClick={handleNext}
//               disabled={!canContinue() || generating}
//               style={{
//                 flex: 1, padding: "13px 24px", borderRadius: "10px",
//                 cursor: canContinue() ? "pointer" : "not-allowed",
//                 border: "none",
//                 background: canContinue() ? "#1D9E75" : "#d1d5db",
//                 color: "#fff", fontSize: "14px", fontWeight: "600",
//                 transition: "all 0.2s",
//               }}
//             >
//               {generating
//                 ? "✨ Generating your plan..."
//                 : isLastStep
//                   ? "🚀 Generate my plan"
//                   : "Continue →"}
//             </button>
//           </div>

//           {/* Step counter */}
//           <div style={{
//             textAlign: "center", marginTop: "16px",
//             fontSize: "12px", color: "#9ca3af",
//           }}>
//             Step {currentStep + 1} of {STEPS.length}
//           </div>
//         </div>

//         {/* Footer note */}
//         <div style={{
//           textAlign: "center", marginTop: "20px",
//           fontSize: "12px", color: "#9ca3af",
//         }}>
//           🔒 Your data is used only to generate your plan
//         </div>
//       </div>
//     </div>
//   )
// }
import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { useAuth } from "../context/AuthContext"
import { submitOnboarding } from "../api/users"
import { setSport } from "../api/storage"
import { apiErrorMessage } from "../api/client"
import '../styles/main.css'

// ─── Data ────────────────────────────────────────────────────────────────────

// Maps the frontend option values to the strings the backend UserProfile expects.
const LEVEL_MAP = {
  beginner: "Beginner",
  some: "Some experience",
  intermediate: "Intermediate",
  advanced: "Advanced",
}
const GOAL_MAP = {
  muscle: "Build muscle",
  weight: "Lose weight",
  fit: "Get fitter",
  health: "Stay healthy",
}

const STEPS = [
  {
    id: "level",
    title: "What's your fitness level?",
    subtitle: "Be honest — your plan adapts to where you are right now.",
    type: "single",
    options: [
      { value: "beginner",     label: "Complete beginner",  icon: "🌱", desc: "Little to no training experience" },
      { value: "some",         label: "Some experience",    icon: "🔄", desc: "Trained on and off before" },
      { value: "intermediate", label: "Intermediate",       icon: "💪", desc: "Training consistently 6+ months" },
      { value: "advanced",     label: "Advanced",           icon: "🏆", desc: "2+ years of structured training" },
    ],
  },
  {
    id: "goal",
    title: "What's your main goal?",
    subtitle: "Your AI plan is optimised around this.",
    type: "single",
    options: [
      { value: "muscle",  label: "Build muscle",  icon: "💪", desc: "Increase strength and size" },
      { value: "weight",  label: "Lose weight",   icon: "🔥", desc: "Burn fat, improve body comp" },
      { value: "fit",     label: "Get fitter",    icon: "⚡", desc: "Improve endurance and stamina" },
      { value: "health",  label: "Stay healthy",  icon: "❤️", desc: "General wellbeing and mobility" },
    ],
  },
  {
    id: "days",
    title: "How many days per week?",
    subtitle: "Pick what's realistic for your schedule — consistency beats intensity.",
    type: "single",
    options: [
      { value: "2", label: "2 days",  icon: "📅", desc: "Light commitment, great start" },
      { value: "3", label: "3 days",  icon: "📅", desc: "Recommended for beginners" },
      { value: "4", label: "4 days",  icon: "📅", desc: "Good for faster progress" },
      { value: "5", label: "5+ days", icon: "📅", desc: "High commitment" },
    ],
  },
  {
    id: "location",
    title: "Where do you train?",
    subtitle: "Your exercises will be selected for your environment.",
    type: "single",
    options: [
      { value: "gym",     label: "Gym",       icon: "🏋️", desc: "Full equipment available" },
      { value: "home",    label: "Home",      icon: "🏠", desc: "Bodyweight or minimal gear" },
      { value: "outdoor", label: "Outdoors",  icon: "🌳", desc: "Parks, tracks, open spaces" },
      { value: "mixed",   label: "Mixed",     icon: "🔀", desc: "Varies day to day" },
    ],
  },
  {
    id: "equipment",
    title: "What equipment do you have?",
    subtitle: "Select all that apply.",
    type: "multi",
    options: [
      { value: "dumbbells",    label: "Dumbbells",        icon: "🏋️" },
      { value: "barbell",      label: "Barbell & rack",   icon: "🔩" },
      { value: "pullup",       label: "Pull-up bar",      icon: "🔝" },
      { value: "bands",        label: "Resistance bands", icon: "〰️" },
      { value: "kettlebell",   label: "Kettlebell",       icon: "⚫" },
      { value: "none",         label: "No equipment",     icon: "🤲" },
    ],
  },
  {
    id: "sport",
    title: "Which sport is your focus?",
    subtitle: "Your AI plan and coach matches are built around this.",
    type: "single",
    options: [
      { value: "Strength Training", label: "Strength Training", icon: "🏋️", desc: "Weights, resistance, muscle building" },
      { value: "Running",           label: "Running",           icon: "🏃", desc: "Endurance and cardio on the road" },
      { value: "Cycling",           label: "Cycling",           icon: "🚴", desc: "Indoor or outdoor riding" },
      { value: "HIIT",              label: "HIIT",              icon: "⚡", desc: "High-intensity interval training" },
      { value: "Yoga",              label: "Yoga & Mobility",   icon: "🧘", desc: "Flexibility, balance, recovery" },
      { value: "Swimming",          label: "Swimming",          icon: "🏊", desc: "Full-body, low-impact cardio" },
    ],
  },
]

const STEP_LABELS = ["Level", "Goal", "Schedule", "Location", "Equipment", "Sport"]

// ─── Sub-components ──────────────────────────────────────────────────────────

function ProgressBar({ current, total }) {
  return (
    <div style={{ marginBottom: "32px" }}>
      <div style={{
        display: "flex", justifyContent: "space-between",
        alignItems: "center", marginBottom: "10px"
      }}>
        {STEP_LABELS.map((label, i) => (
          <div key={i} style={{
            display: "flex", flexDirection: "column",
            alignItems: "center", gap: "5px", flex: 1,
          }}>
            <div style={{
              width: "28px", height: "28px", borderRadius: "50%",
              display: "flex", alignItems: "center", justifyContent: "center",
              fontSize: "12px", fontWeight: "600", transition: "all 0.25s",
              background: i < current ? "#1D9E75" : i === current ? "#fff" : "transparent",
              color: i < current ? "#fff" : i === current ? "#1D9E75" : "#9ca3af",
              border: i === current ? "2px solid #1D9E75" : i < current ? "2px solid #1D9E75" : "2px solid #e5e7eb",
            }}>
              {i < current ? "✓" : i + 1}
            </div>
            <span style={{
              fontSize: "10px", fontWeight: i === current ? "600" : "400",
              color: i === current ? "#1D9E75" : i < current ? "#0F6E56" : "#9ca3af",
            }}>
              {label}
            </span>
          </div>
        ))}
      </div>
      <div style={{
        height: "3px", background: "#e5e7eb", borderRadius: "2px",
        marginTop: "4px", overflow: "hidden",
      }}>
        <div style={{
          height: "100%", background: "#1D9E75", borderRadius: "2px",
          width: `${(current / (total - 1)) * 100}%`,
          transition: "width 0.4s ease",
        }} />
      </div>
    </div>
  )
}

function OptionCard({ option, selected, multi, onToggle }) {
  const isSelected = multi
    ? selected.includes(option.value)
    : selected === option.value

  return (
    <button
      onClick={() => onToggle(option.value)}
      style={{
        display: "flex", alignItems: "flex-start", gap: "12px",
        padding: "14px 16px", borderRadius: "12px", cursor: "pointer",
        border: isSelected ? "2px solid #1D9E75" : "1.5px solid #e5e7eb",
        background: isSelected ? "#f0fdf8" : "#fff",
        textAlign: "left", transition: "all 0.15s", width: "100%",
        outline: "none",
      }}
    >
      <span style={{ fontSize: "22px", lineHeight: 1, marginTop: "1px" }}>{option.icon}</span>
      <div style={{ flex: 1 }}>
        <div style={{
          fontSize: "14px", fontWeight: "600",
          color: isSelected ? "#0F6E56" : "#111827",
        }}>
          {option.label}
        </div>
        {option.desc && (
          <div style={{ fontSize: "12px", color: "#6b7280", marginTop: "2px" }}>
            {option.desc}
          </div>
        )}
      </div>
      <div style={{
        width: "18px", height: "18px", borderRadius: multi ? "4px" : "50%",
        border: isSelected ? "none" : "1.5px solid #d1d5db",
        background: isSelected ? "#1D9E75" : "transparent",
        display: "flex", alignItems: "center", justifyContent: "center",
        flexShrink: 0, marginTop: "2px", transition: "all 0.15s",
      }}>
        {isSelected && (
          <svg width="10" height="8" viewBox="0 0 10 8" fill="none">
            <path d="M1 4L3.5 6.5L9 1" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        )}
      </div>
    </button>
  )
}

function SummaryCard({ answers }) {
  const labels = {
    level:     { beginner: "Complete beginner", some: "Some experience", intermediate: "Intermediate", advanced: "Advanced" },
    goal:      { muscle: "Build muscle", weight: "Lose weight", fit: "Get fitter", health: "Stay healthy" },
    days:      { "2": "2 days/week", "3": "3 days/week", "4": "4 days/week", "5": "5+ days/week" },
    location:  { gym: "Gym", home: "Home", outdoor: "Outdoors", mixed: "Mixed" },
    equipment: answers.equipment?.join(", ") || "—",
  }

  const rows = [
    { icon: "🌱", label: "Fitness level", value: labels.level[answers.level] },
    { icon: "🎯", label: "Goal",          value: labels.goal[answers.goal] },
    { icon: "📅", label: "Days/week",     value: labels.days[answers.days] },
    { icon: "📍", label: "Location",      value: labels.location[answers.location] },
    { icon: "🏋️", label: "Equipment",    value: labels.equipment },
    { icon: "🏅", label: "Sport",         value: answers.sport || "—" },
  ]

  return (
    <div style={{
      background: "#f0fdf8", border: "1.5px solid #5DCAA5",
      borderRadius: "14px", padding: "20px", marginBottom: "24px",
    }}>
      <div style={{ fontSize: "13px", fontWeight: "600", color: "#0F6E56", marginBottom: "14px" }}>
        ✨ Your profile summary
      </div>
      {rows.map((r, i) => (
        <div key={i} style={{
          display: "flex", alignItems: "center", gap: "10px",
          padding: "7px 0",
          borderBottom: i < rows.length - 1 ? "1px solid #d1fae5" : "none",
        }}>
          <span style={{ fontSize: "16px" }}>{r.icon}</span>
          <span style={{ fontSize: "13px", color: "#6b7280", flex: 1 }}>{r.label}</span>
          <span style={{ fontSize: "13px", fontWeight: "600", color: "#0F6E56" }}>{r.value}</span>
        </div>
      ))}
    </div>
  )
}

// ─── Main Component ──────────────────────────────────────────────────────────

export default function Onboarding() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [currentStep, setCurrentStep] = useState(0)
  const [answers, setAnswers] = useState({
    level: null, goal: null, days: null, location: null, equipment: [], sport: null,
  })
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState("")

  const step = STEPS[currentStep]
  const isLastStep = currentStep === STEPS.length - 1

  function handleSelect(value) {
    if (step.type === "multi") {
      setAnswers(prev => {
        const current = prev.equipment
        return {
          ...prev,
          equipment: current.includes(value)
            ? current.filter(v => v !== value)
            : [...current, value],
        }
      })
    } else {
      setAnswers(prev => ({ ...prev, [step.id]: value }))
    }
  }

  function canContinue() {
    if (step.type === "multi") return answers.equipment.length > 0
    return answers[step.id] !== null
  }

  async function handleNext() {
    if (!canContinue()) return
    if (isLastStep) {
      setGenerating(true)
      setError("")
      try {
        // location & equipment stay frontend-only — the backend profile only
        // stores fitnessGoal / fitnessLevel / trainingFrequency (+ sportTypeName).
        await submitOnboarding(user.userId, {
          fitnessGoal: GOAL_MAP[answers.goal],
          fitnessLevel: LEVEL_MAP[answers.level],
          trainingFrequency: Number(answers.days),
          sportTypeName: answers.sport,
        })
        // Remember the chosen sport so plan generation can default to it.
        setSport(answers.sport)
        navigate("/plan") // redirect to training plan page
      } catch (err) {
        setError(apiErrorMessage(err, "Could not save your profile. Please try again."))
      } finally {
        setGenerating(false)
      }
    } else {
      setCurrentStep(prev => prev + 1)
    }
  }

  function handleBack() {
    if (currentStep > 0) setCurrentStep(prev => prev - 1)
  }

  return (
    <div style={{
      minHeight: "100vh", display: "flex", alignItems: "center",
      justifyContent: "center", padding: "24px",
      background: "linear-gradient(135deg, #f0fdf8 0%, #ffffff 60%)",
    }}>
      <div style={{ width: "100%", maxWidth: "520px" }}>

        {/* Header */}
        <div style={{ textAlign: "center", marginBottom: "36px" }}>
          <div style={{ fontSize: "26px", fontWeight: "700", color: "#0F6E56", marginBottom: "6px" }}>
            Fit<span style={{ color: "#1D9E75" }}>Connect</span>
          </div>
          <div style={{ fontSize: "13px", color: "#6b7280" }}>
            Let's build your personalised training plan
          </div>
        </div>

        {/* Card */}
        <div style={{
          background: "#fff", borderRadius: "20px",
          boxShadow: "0 4px 24px rgba(0,0,0,0.07)",
          padding: "32px",
        }}>
          <ProgressBar current={currentStep} total={STEPS.length} />

          {/* Step heading */}
          <div style={{ marginBottom: "20px" }}>
            <h1 style={{ fontSize: "20px", fontWeight: "700", color: "#111827", marginBottom: "6px" }}>
              {step.title}
            </h1>
            <p style={{ fontSize: "13px", color: "#6b7280", lineHeight: "1.5" }}>
              {step.subtitle}
            </p>
          </div>

          {/* Summary on last step */}
          {isLastStep && <SummaryCard answers={answers} />}

          {/* Options */}
          <div style={{
            display: "grid",
            gridTemplateColumns: step.type === "multi" ? "1fr 1fr" : "1fr",
            gap: "10px", marginBottom: "24px",
          }}>
            {step.options.map(option => (
              <OptionCard
                key={option.value}
                option={option}
                selected={step.type === "multi" ? answers.equipment : answers[step.id]}
                multi={step.type === "multi"}
                onToggle={handleSelect}
              />
            ))}
          </div>

          {/* Error */}
          {error && (
            <div style={{
              background: "#fef2f2", border: "1px solid #fecaca", color: "#dc2626",
              borderRadius: "10px", padding: "10px 12px", fontSize: "12px", marginBottom: "14px",
            }}>
              {error}
            </div>
          )}

          {/* Actions */}
          <div style={{ display: "flex", gap: "10px" }}>
            {currentStep > 0 && (
              <button
                onClick={handleBack}
                style={{
                  padding: "12px 20px", borderRadius: "10px", cursor: "pointer",
                  border: "1.5px solid #e5e7eb", background: "#fff",
                  fontSize: "14px", fontWeight: "500", color: "#374151",
                  transition: "all 0.15s",
                }}
              >
                ← Back
              </button>
            )}
            <button
              onClick={handleNext}
              disabled={!canContinue() || generating}
              style={{
                flex: 1, padding: "13px 24px", borderRadius: "10px",
                cursor: canContinue() ? "pointer" : "not-allowed",
                border: "none",
                background: canContinue() ? "#1D9E75" : "#d1d5db",
                color: "#fff", fontSize: "14px", fontWeight: "600",
                transition: "all 0.2s",
              }}
            >
              {generating
                ? "✨ Generating your plan..."
                : isLastStep
                  ? "🚀 Generate my plan"
                  : "Continue →"}
            </button>
          </div>

          {/* Step counter */}
          <div style={{
            textAlign: "center", marginTop: "16px",
            fontSize: "12px", color: "#9ca3af",
          }}>
            Step {currentStep + 1} of {STEPS.length}
          </div>
        </div>

        {/* Footer note */}
        <div style={{
          textAlign: "center", marginTop: "20px",
          fontSize: "12px", color: "#9ca3af",
        }}>
          🔒 Your data is used only to generate your plan
        </div>
      </div>
    </div>
  )
}