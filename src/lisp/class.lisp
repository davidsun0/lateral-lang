(defclass name (:extends parent)
  ; (defclass name {:extends parent :modifiers (:final :public)}
  ; public abstract
  ; types
  {:extends parent}

  ; initializing to (body)
  (field type name (body))
  ; uninitialized
  (deffield type name)
  (field type name)

  ; public static void main(String[] args)
  (defmethod main (:public :static) (:void ("String[]" args))
    (body))

  (defmethod name (args)
    MethodType
    modifiers
    (body))
  )
